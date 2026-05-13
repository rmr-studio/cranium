package cranium.core.service.workspace

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import cranium.core.entity.workspace.WorkspaceEntity
import cranium.core.entity.workspace.WorkspaceMemberEntity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.exceptions.NotFoundException
import cranium.core.models.analytics.WorkspaceCreatedEvent
import cranium.core.models.analytics.WorkspaceUpdatedEvent
import cranium.core.models.common.markDeleted
import cranium.core.models.request.workspace.SaveWorkspaceRequest
import cranium.core.models.websocket.WorkspaceChangeEvent
import cranium.core.models.request.user.SaveUserRequest
import cranium.core.models.workspace.Workspace
import cranium.core.models.workspace.WorkspaceMember
import cranium.core.repository.workspace.WorkspaceMemberRepository
import cranium.core.repository.workspace.WorkspaceRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.activity.log
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.storage.StorageService
import cranium.core.service.user.UserService
import cranium.core.util.ServiceUtil.findOrThrow
import java.util.*

@Service
class WorkspaceService(
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceMemberRepository: WorkspaceMemberRepository,
    private val userService: UserService,
    private val logger: KLogger,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val storageService: StorageService
) {
    /**
     * Retrieve an workspace by its ID, optionally including metadata.
     *
     * @param includeMetadata When true, include additional metadata such as audit information and team members.
     * @return The workspace model corresponding to the given ID.
     * @throws NotFoundException If no workspace exists with the provided ID.
     */
    @Throws(NotFoundException::class)
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getWorkspaceById(workspaceId: UUID, includeMetadata: Boolean = false): Workspace {
        return getEntityById(workspaceId).toModel(includeMetadata)
    }

    /**
     * Retrieve the WorkspaceEntity for the given workspaceId.
     *
     * @param workspaceId The UUID of the workspace to fetch.
     * @return The matching WorkspaceEntity.
     * @throws NotFoundException If no workspace exists with the provided id.
     */
    @Throws(NotFoundException::class)
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getEntityById(workspaceId: UUID): WorkspaceEntity {
        return findOrThrow { workspaceRepository.findById(workspaceId) }
    }

    /**
     * Whether the workspace has opted into SENTIMENT-metadata sentiment analysis during
     * enrichment. Default false. RELATIONAL and STRUCTURAL metadata are unaffected.
     */
    @Throws(NotFoundException::class)
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun isConnotationEnabled(workspaceId: UUID): Boolean {
        val workspace = findOrThrow { workspaceRepository.findById(workspaceId) }
        return workspace.connotationEnabled
    }

    /**
     * Creates or updates a workspace, then uploads avatar outside the transaction boundary
     * to prevent orphaned files on rollback.
     */
    @Transactional
    @Throws(AccessDeniedException::class, IllegalArgumentException::class)
    fun saveWorkspace(request: SaveWorkspaceRequest, avatar: MultipartFile? = null): Workspace {
        val userId = authTokenService.getUserId()
        val currency = getCurrency(request.defaultCurrency)
        val isUpdate = request.id != null

        if (isUpdate) {
            findOrThrow { workspaceMemberRepository.findByWorkspaceIdAndUserId(request.id!!, userId) }
        }

        val entity = createOrUpdateWorkspaceEntity(request, currency)
        val previousAvatarKey = if (isUpdate) entity.avatarUrl else null

        // Handle explicit avatar removal
        if (request.removeAvatar && entity.avatarUrl != null) {
            entity.avatarUrl = null
        }

        val saved = workspaceRepository.save(entity)
        val workspaceId = requireNotNull(saved.id) { "WorkspaceEntity must have a non-null id after save" }

        logWorkspaceActivity(userId, workspaceId, saved.name, isUpdate)

        if (!isUpdate) {
            createOwnerMember(workspaceId, userId)
        }

        publishWorkspaceAnalytics(saved, request, userId)

        val workspace = saved.toModel().also { workspace ->
            setDefaultWorkspaceIfNeeded(workspace, request, userId)
        }

        avatar?.let { file ->
            val newKey = storageService.updateWorkspaceAvatar(workspace.id, file, previousAvatarKey)
            saved.avatarUrl = newKey
            workspaceRepository.save(saved)
            return workspace.copy(avatarUrl = newKey)
        }

        // Clean up old avatar if it was removed
        if (request.removeAvatar) {
            storageService.removeAvatar(previousAvatarKey)
        }

        return workspace
    }


    private fun createOrUpdateWorkspaceEntity(request: SaveWorkspaceRequest, currency: Currency): WorkspaceEntity {
        return if (request.id == null) {
            WorkspaceEntity(
                name = request.name,
                plan = request.plan,
                defaultCurrency = currency,
            )
        } else {
            findOrThrow { workspaceRepository.findById(request.id) }.apply {
                this.name = request.name
                this.plan = request.plan
                this.defaultCurrency = currency
            }
        }
    }

    private fun createOwnerMember(workspaceId: UUID, userId: UUID) {
        workspaceMemberRepository.save(
            WorkspaceMemberEntity(
                workspaceId = workspaceId,
                userId = userId,
                role = WorkspaceRoles.OWNER
            )
        )
    }

    private fun logWorkspaceActivity(userId: UUID, workspaceId: UUID, name: String, isUpdate: Boolean = false) {
        activityService.log(
            activity = cranium.core.enums.activity.Activity.WORKSPACE,
            operation = if (isUpdate) OperationType.UPDATE else OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.WORKSPACE,
            entityId = workspaceId,
            "workspaceId" to workspaceId.toString(),
            "name" to name
        )
    }

    private fun publishWorkspaceAnalytics(entity: WorkspaceEntity, request: SaveWorkspaceRequest, userId: UUID) {
        val id = requireNotNull(entity.id)
        val analyticsEvent = if (request.id == null) {
            WorkspaceCreatedEvent(
                workspaceId = id,
                workspaceName = entity.name,
                createdAt = entity.createdAt,
                memberCount = 1,
                userId = userId
            )
        } else {
            WorkspaceUpdatedEvent(
                workspaceId = id,
                workspaceName = entity.name,
                createdAt = entity.createdAt,
                memberCount = entity.memberCount,
                userId = userId
            )
        }
        applicationEventPublisher.publishEvent(analyticsEvent)

        applicationEventPublisher.publishEvent(
            WorkspaceChangeEvent(
                workspaceId = id,
                userId = userId,
                operation = if (request.id == null) OperationType.CREATE else OperationType.UPDATE,
                entityId = id,
                summary = mapOf(
                    "name" to entity.name,
                    "userDisplayName" to authTokenService.getUserDisplayName(),
                ),
            )
        )
    }

    private fun setDefaultWorkspaceIfNeeded(workspace: Workspace, request: SaveWorkspaceRequest, userId: UUID) {
        val user = userService.getUserWithWorkspacesFromSession()
        if (user.memberships.isEmpty() || request.isDefault) {
            val saveRequest = SaveUserRequest(
                name = user.name,
                email = user.email,
                phone = user.phone,
                defaultWorkspaceId = workspace.id,
                onboardingCompletedAt = user.onboardingCompletedAt,
            )
            userService.updateUserDetails(saveRequest)
        }
    }

    private fun getCurrency(symbol: String): Currency {
        return try {
            Currency.getInstance(symbol.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid currency code: ${symbol}")
        }
    }

    /**
     * Deletes the workspace identified by [workspaceId] along with all associated membership records.
     *
     * This operation is transactional and logs an WORKSPACE DELETE activity that includes the workspace name.
     *
     * @param workspaceId The UUID of the workspace to delete.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'OWNER')")
    @Transactional
    fun deleteWorkspace(workspaceId: UUID) {
        authTokenService.getUserId().let { userId ->
            // Check if the workspace exists
            val workspace: WorkspaceEntity = findOrThrow { workspaceRepository.findById(workspaceId) }

            // Delete all members associated with the workspace


            // Delete the workspace itself
            workspace.markDeleted().run {
                workspaceRepository.save(this)
                // Log the activity of deleting an workspace
                activityService.log(
                    activity = cranium.core.enums.activity.Activity.WORKSPACE,
                    operation = OperationType.DELETE,
                    userId = userId,
                    workspaceId = workspaceId,
                    entityType = ApplicationEntityType.WORKSPACE,
                    entityId = workspaceId,
                    "workspaceId" to workspaceId.toString(),
                    "name" to workspace.name
                )

            }
        }
    }

    /**
     * Invoked from Invitation accept action. Users cannot directly add others to an workspace.
     */
    fun addMemberToWorkspace(workspaceId: UUID, userId: UUID, role: WorkspaceRoles): WorkspaceMember {
        // Create and save the new member entity


        return WorkspaceMemberEntity(
            workspaceId = workspaceId,
            userId = userId,
            role = role
        ).run {
            workspaceMemberRepository.save(this).let { entity ->
                entity.toModel()
            }.also {
                logger.info { "User with ID $userId added to workspace $workspaceId with role $role." }
            }
        }
    }


    fun removeMemberFromWorkspace(workspaceId: UUID, memberId: UUID) {
        authTokenService.getUserId().let { userId ->


            val member = findOrThrow { workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId) }
            val id = requireNotNull(member.id) { "User with ID $memberId does not exist." }
            // Assert that the removed member is not currently the owner of the workspace
            if (member.role == WorkspaceRoles.OWNER) {
                throw IllegalArgumentException("Cannot remove the owner of the workspace. Please transfer ownership first.")
            }

            workspaceMemberRepository.deleteById(id).also {
                activityService.log(
                    activity = cranium.core.enums.activity.Activity.WORKSPACE_MEMBER,
                    operation = OperationType.DELETE,
                    userId = userId,
                    workspaceId = workspaceId,
                    entityType = ApplicationEntityType.USER,
                    entityId = id,
                    "userId" to id.toString(),
                    "workspaceId" to workspaceId.toString()
                )
            }
        }
    }


    fun updateMemberRole(
        workspaceId: UUID,
        memberId: UUID,
        role: WorkspaceRoles
    ): WorkspaceMember {
        authTokenService.getUserId().let { userId ->

            val member = findOrThrow { workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, memberId) }

            // Ensure that if the new role is that of OWNER, that only the current owner can assign it
            if (role == WorkspaceRoles.OWNER || member.role == WorkspaceRoles.OWNER) {
                throw IllegalArgumentException("Transfer of ownership must be done through a dedicated transfer ownership method.")
            }


            member.apply {
                this.role = role
            }

            return workspaceMemberRepository.save(member).let {
                requireNotNull(it.id)
                it.toModel()
            }.also {
                activityService.log(
                    activity = cranium.core.enums.activity.Activity.WORKSPACE_MEMBER,
                    operation = OperationType.UPDATE,
                    userId = userId,
                    workspaceId = workspaceId,
                    entityType = ApplicationEntityType.USER,
                    entityId = memberId,
                    "userId" to memberId.toString(),
                    "workspaceId" to workspaceId.toString(),
                    "role" to role.toString()
                )
            }

        }
    }
}

