package riven.core.service.onboarding

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.models.request.onboarding.CompleteOnboardingRequest
import riven.core.models.request.workspace.SaveWorkspaceRequest
import riven.core.models.response.onboarding.CompleteOnboardingResponse
import riven.core.models.response.onboarding.InviteResult
import riven.core.models.response.onboarding.TemplateInstallResult
import riven.core.models.user.User
import riven.core.models.workspace.Workspace
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.TemplateInstallationService
import riven.core.service.user.UserService
import riven.core.service.workspace.WorkspaceInviteService
import riven.core.service.workspace.WorkspaceService
import java.time.ZonedDateTime
import java.util.*

/**
 * Orchestrates the complete user onboarding flow in a single request.
 *
 * Phase 1 (atomic): Creates workspace and updates user profile within a transaction.
 * Phase 2 (best-effort): Installs templates and sends invitations after commit.
 */
@Service
class OnboardingService(
    private val workspaceService: WorkspaceService,
    private val userService: UserService,
    private val templateInstallationService: TemplateInstallationService,
    private val workspaceInviteService: WorkspaceInviteService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val transactionTemplate: TransactionTemplate,
    private val logger: KLogger,
) {

    /**
     * Complete the full onboarding flow: create workspace, update profile, install templates,
     * and send invitations.
     *
     * @param request the onboarding request containing workspace, profile, template, and invite data
     * @param profileAvatar optional avatar file for the user profile
     * @param workspaceAvatar optional avatar file for the workspace
     * @return response containing created workspace, updated user, and best-effort results
     */
    fun completeOnboarding(
        request: CompleteOnboardingRequest,
        profileAvatar: MultipartFile? = null,
        workspaceAvatar: MultipartFile? = null,
    ): CompleteOnboardingResponse {
        val userId = authTokenService.getUserId()
        validateOnboardingEligibility(userId)

        // Phase 1: Atomic — create workspace + update profile
        val (workspace, user) = transactionTemplate.execute {
            val workspace = createWorkspace(request, workspaceAvatar)
            val user = updateUserProfile(userId, request, workspace, profileAvatar)
            workspace to user
        }!!

        val workspaceId = workspace.id

        // Phase 2: Best-effort — templates + invites
        val templateResults = installTemplatesBestEffort(workspaceId, request)
        val inviteResults = sendInvitesBestEffort(workspaceId, userId, request)

        logger.info { "Onboarding completed for user $userId, workspace $workspaceId" }

        return CompleteOnboardingResponse(
            workspace = workspace,
            user = user,
            templateResults = templateResults,
            inviteResults = inviteResults,
        )
    }

    // ------ Phase 1: Atomic Operations ------

    /**
     * Validates that the user has not already completed onboarding.
     *
     * @throws ConflictException if onboarding has already been completed
     */
    private fun validateOnboardingEligibility(userId: UUID) {
        val userEntity = userService.getUserById(userId)
        if (userEntity.onboardingCompletedAt != null) {
            throw ConflictException("Onboarding has already been completed for this user")
        }
    }

    private fun createWorkspace(request: CompleteOnboardingRequest, avatar: MultipartFile?): Workspace {
        val saveRequest = SaveWorkspaceRequest(
            name = request.workspace.name,
            plan = request.workspace.plan,
            defaultCurrency = request.workspace.defaultCurrency,
            isDefault = true,
        )
        return workspaceService.saveWorkspace(saveRequest, avatar)
    }

    private fun updateUserProfile(
        userId: UUID,
        request: CompleteOnboardingRequest,
        workspace: Workspace,
        profileAvatar: MultipartFile?,
    ): User {
        val currentUser = userService.getUserWithWorkspacesById(userId)
        val updatedUser = currentUser.copy(
            name = request.profile.name,
            phone = request.profile.phone,
            defaultWorkspace = workspace,
            onboardingCompletedAt = ZonedDateTime.now(),
        )

        val user = userService.updateUserDetails(updatedUser, profileAvatar)

        activityService.log(
            activity = Activity.ONBOARDING,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspace.id,
            entityType = ApplicationEntityType.USER,
            entityId = userId,
            "workspaceId" to workspace.id.toString(),
            "workspaceName" to workspace.name,
            "profileName" to request.profile.name,
        )

        return user
    }

    // ------ Phase 2: Best-Effort Operations ------

    private fun installTemplatesBestEffort(
        workspaceId: UUID,
        request: CompleteOnboardingRequest,
    ): List<TemplateInstallResult> {
        val results = mutableListOf<TemplateInstallResult>()

        request.templateKeys?.forEach { templateKey ->
            results.add(installTemplateSafely(workspaceId, templateKey))
        }

        request.bundleKeys?.forEach { bundleKey ->
            results.add(installBundleSafely(workspaceId, bundleKey))
        }

        return results
    }

    private fun installTemplateSafely(workspaceId: UUID, templateKey: String): TemplateInstallResult {
        return try {
            val response = templateInstallationService.installTemplateInternal(workspaceId, templateKey)
            TemplateInstallResult(
                key = templateKey,
                success = true,
                entityTypesCreated = response.entityTypesCreated,
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to install template '$templateKey' during onboarding" }
            TemplateInstallResult(
                key = templateKey,
                success = false,
                error = e.message,
            )
        }
    }

    private fun installBundleSafely(workspaceId: UUID, bundleKey: String): TemplateInstallResult {
        return try {
            val response = templateInstallationService.installBundleInternal(workspaceId, bundleKey)
            TemplateInstallResult(
                key = bundleKey,
                success = true,
                entityTypesCreated = response.entityTypesCreated,
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to install bundle '$bundleKey' during onboarding" }
            TemplateInstallResult(
                key = bundleKey,
                success = false,
                error = e.message,
            )
        }
    }

    private fun sendInvitesBestEffort(
        workspaceId: UUID,
        userId: UUID,
        request: CompleteOnboardingRequest,
    ): List<InviteResult> {
        val invites = request.invites ?: return emptyList()
        val userEmail = authTokenService.getUserEmail()

        return invites.map { invite ->
            sendInviteSafely(workspaceId, userId, invite.email, invite.role, userEmail)
        }
    }

    private fun sendInviteSafely(
        workspaceId: UUID,
        userId: UUID,
        email: String,
        role: WorkspaceRoles,
        userEmail: String,
    ): InviteResult {
        if (email.equals(userEmail, ignoreCase = true)) {
            return InviteResult(
                email = email,
                success = false,
                error = "Cannot invite yourself to your own workspace",
            )
        }

        if (role == WorkspaceRoles.OWNER) {
            return InviteResult(
                email = email,
                success = false,
                error = "Cannot invite with OWNER role",
            )
        }

        return try {
            workspaceInviteService.createWorkspaceInvitationInternal(workspaceId, email, role, userId)
            InviteResult(email = email, success = true)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send invite to '$email' during onboarding" }
            InviteResult(email = email, success = false, error = e.message)
        }
    }
}
