package riven.core.service.onboarding

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.BusinessType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.models.request.knowledge.CreateBusinessDefinitionRequest
import riven.core.models.request.onboarding.CompleteOnboardingRequest
import riven.core.models.request.onboarding.OnboardingBusinessDefinition
import riven.core.models.request.onboarding.OnboardingInvite
import riven.core.models.request.workspace.SaveWorkspaceRequest
import riven.core.models.response.onboarding.BusinessDefinitionResult
import riven.core.models.response.onboarding.CompleteOnboardingResponse
import riven.core.models.response.onboarding.InviteResult
import riven.core.models.response.onboarding.TemplateInstallResult
import riven.core.models.request.user.SaveUserRequest
import riven.core.models.user.User
import riven.core.models.workspace.Workspace
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.TemplateInstallationService
import riven.core.service.knowledge.WorkspaceBusinessDefinitionService
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
    private val businessDefinitionService: WorkspaceBusinessDefinitionService,
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

        // Phase 1: Atomic — eligibility check + create workspace + update profile
        val (workspace, user) = transactionTemplate.execute {
            validateOnboardingEligibility(userId)
            val workspace = createWorkspace(request, workspaceAvatar)
            val user = updateUserProfile(userId, request, workspace, profileAvatar)
            workspace to user
        }!!

        val workspaceId = workspace.id

        // After initial transaction, we can start populating workspace specific data without blocking the user. These operations are best-effort and won't fail the entire flow if they encounter issues.
        val templateResult = populateWorkspace(workspaceId, request.businessType)
        val inviteResults = request.invites?.let {
            sendWorkspaceInvites(workspaceId, userId, it)
        }.orEmpty()

        val definitionResults = request.businessDefinitions?.let {
            saveWorkspaceDefinitions(workspaceId, userId, it)
         }.orEmpty()


        logger.info { "Onboarding completed for user $userId, workspace $workspaceId" }

        return CompleteOnboardingResponse(
            workspace = workspace,
            user = user.toDisplay(),
            templateResult = templateResult,
            inviteResults = inviteResults,
            definitionResults = definitionResults,
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
        val saveRequest = SaveUserRequest(
            name = request.profile.name,
            email = currentUser.email,
            phone = request.profile.phone,
            defaultWorkspaceId = workspace.id,
            onboardingCompletedAt = ZonedDateTime.now(),
            acquisitionChannels = request.acquisitionChannels,
        )

        val user = userService.updateUserDetails(saveRequest, profileAvatar)

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

    private fun populateWorkspace(
        id: UUID,
        type: BusinessType
    ): TemplateInstallResult {
        return try {
            val response = templateInstallationService.installTemplate(id, type.templateKey)
            TemplateInstallResult(
                key = type.templateKey,
                success = true,
                entityTypesCreated = response.entityTypesCreated,
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to install template '${type.templateKey}' during onboarding" }
            TemplateInstallResult(
                key = type.templateKey,
                success = false,
                error = e.message,
            )
        }
    }

    private fun sendWorkspaceInvites(id: UUID, userId: UUID, invites: List<OnboardingInvite>): List<InviteResult> {
        val userEmail = authTokenService.getUserEmail()

        return invites.map { invite ->
            val (email, role) = invite
            if (email.equals(userEmail, ignoreCase = true)) {
                return@map InviteResult(
                    email = email,
                    success = false,
                    error = "Cannot invite yourself to your own workspace",
                )
            }

            if (role == WorkspaceRoles.OWNER) {
                return@map InviteResult(
                    email = email,
                    success = false,
                    error = "Cannot invite with OWNER role",
                )
            }

            try {
                workspaceInviteService.createWorkspaceInvitationInternal(id, email, role, userId)
                InviteResult(email = email, success = true)
            } catch (e: Exception) {
                logger.error(e) { "Failed to send invite to '$email' during onboarding" }
                InviteResult(email = email, success = false, error = e.message)
            }
        }
    }

    private fun saveWorkspaceDefinitions(
        id: UUID,
        userId: UUID,
        definitions: List<OnboardingBusinessDefinition>
    ): List<BusinessDefinitionResult> {

        return definitions.map { definition ->
            try {
                val createRequest = CreateBusinessDefinitionRequest(
                    term = definition.term,
                    definition = definition.definition,
                    category = definition.category,
                    source = DefinitionSource.ONBOARDING,
                )
                businessDefinitionService.createDefinitionInternal(id, userId, createRequest)
                BusinessDefinitionResult(term = definition.term, success = true)
            } catch (e: Exception) {
                logger.error(e) { "Failed to save business definition '${definition.term}' during onboarding" }
                BusinessDefinitionResult(term = definition.term, success = false, error = e.message)
            }
        }
    }
}

