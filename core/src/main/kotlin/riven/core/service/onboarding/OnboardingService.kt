package riven.core.service.onboarding

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.exceptions.NotFoundException
import riven.core.exceptions.UniqueConstraintViolationException
import riven.core.models.core.DTC_ECOMMERCE_MODELS
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
import riven.core.repository.user.UserRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.catalog.TemplateInstallationService
import riven.core.service.knowledge.GlossaryService
import riven.core.service.user.UserService
import riven.core.service.workspace.WorkspaceInviteService
import riven.core.service.workspace.WorkspaceService
import riven.core.util.ServiceUtil
import java.time.ZonedDateTime
import java.util.*

/**
 * Orchestrates the complete user onboarding flow in a single request.
 *
 * Phase 1 (atomic): Creates workspace, installs template, and updates user profile within a transaction.
 * Phase 2 (best-effort): Sends invitations and saves business definitions after commit.
 */
@Service
class OnboardingService(
    private val workspaceService: WorkspaceService,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val templateInstallationService: TemplateInstallationService,
    private val workspaceInviteService: WorkspaceInviteService,
    private val businessDefinitionService: GlossaryService,
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

        // Phase 1: Atomic — eligibility check + create workspace + install template + update profile
        val (workspace, user, templateResult) = transactionTemplate.execute {
            val userEntity = ServiceUtil.findOrThrow { userRepository.findByIdForUpdate(userId) }
            if (userEntity.onboardingCompletedAt != null) {
                throw ConflictException("User has already completed onboarding")
            }
            val workspace = createWorkspace(request, workspaceAvatar)
            val templateResult = populateWorkspace(workspace.id, userId)
            val user = updateUserProfile(userId, request, workspace, profileAvatar)
            Triple(workspace, user, templateResult)
        }!!

        val workspaceId = workspace.id

        // Phase 2: Best-effort — invites and definitions don't block onboarding completion
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

    private fun createWorkspace(request: CompleteOnboardingRequest, avatar: MultipartFile?): Workspace {
        val saveRequest = SaveWorkspaceRequest(
            name = request.workspace.name,
            plan = request.workspace.plan,
            defaultCurrency = request.workspace.defaultCurrency,
            isDefault = true,
        )
        return workspaceService.saveWorkspace(saveRequest, avatar)
    }

    private fun populateWorkspace(
        id: UUID,
        userId: UUID,
    ): TemplateInstallResult {
        val templateKey = DTC_ECOMMERCE_MODELS.manifestKey
        val response = templateInstallationService.installTemplateInternal(id, templateKey, userId)
        return TemplateInstallResult(
            key = templateKey,
            entityTypesCreated = response.entityTypesCreated,
            relationshipsCreated = response.relationshipsCreated,
        )
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
            } catch (e: NotFoundException) {
                logger.error(e) { "Failed to send invite to '$email' during onboarding" }
                InviteResult(email = email, success = false, error = "invite_not_found")
            } catch (e: ConflictException) {
                logger.error(e) { "Failed to send invite to '$email' during onboarding" }
                InviteResult(email = email, success = false, error = "invite_conflict")
            } catch (e: Exception) {
                logger.error(e) { "Failed to send invite to '$email' during onboarding" }
                throw e
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
                    isCustomized = definition.isCustomized,
                )
                businessDefinitionService.createDefinitionInternal(id, userId, createRequest)
                BusinessDefinitionResult(term = definition.term, success = true)
            } catch (e: ConflictException) {
                logger.error(e) { "Failed to save business definition '${definition.term}' during onboarding" }
                BusinessDefinitionResult(term = definition.term, success = false, error = "definition_conflict")
            } catch (e: UniqueConstraintViolationException) {
                logger.error(e) { "Failed to save business definition '${definition.term}' during onboarding" }
                BusinessDefinitionResult(term = definition.term, success = false, error = "definition_duplicate")
            } catch (e: Exception) {
                logger.error(e) { "Failed to save business definition '${definition.term}' during onboarding" }
                throw e
            }
        }
    }
}

