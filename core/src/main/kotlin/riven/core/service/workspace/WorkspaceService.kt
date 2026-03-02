package riven.core.service.workspace

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import riven.core.entity.workspace.WorkspaceEntity
import riven.core.entity.workspace.WorkspaceMemberEntity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.NotFoundException
import riven.core.models.request.workspace.SaveWorkspaceRequest
import riven.core.models.workspace.Workspace
import riven.core.models.workspace.WorkspaceMember
import riven.core.repository.workspace.WorkspaceMemberRepository
import riven.core.repository.workspace.WorkspaceRepository
import org.springframework.context.ApplicationEventPublisher
import riven.core.models.analytics.WorkspaceCreatedEvent
import riven.core.models.analytics.WorkspaceUpdatedEvent
import riven.core.models.common.markDeleted
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.user.UserService
import riven.core.util.ServiceUtil.findOrThrow
import java.util.*

@Service
class WorkspaceService(
    private val workspaceRepository: WorkspaceRepository,
    private val workspaceMemberRepository: WorkspaceMemberRepository,
    private val userService: UserService,
    private val logger: KLogger,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val applicationEventPublisher: ApplicationEventPublisher
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
     * Transactional given our createWorkspace method creates both an Workspace and its first member.
     */
    @Throws(AccessDeniedException::class, IllegalArgumentException::class)
    @Transactional
    fun saveWorkspace(request: SaveWorkspaceRequest, avatar: MultipartFile? = null): Workspace {
        // Gets the user ID from the auth token to act as the Workspace creator
        authTokenService.getUserId().let { userId ->
            val currency: Currency = getCurrency(request.defaultCurrency)

            // Create and save the workspace entity
            val entity = request.id.let {
                if (it == null) {
                    return@let WorkspaceEntity(
                        name = request.name,
                        plan = request.plan,
                        defaultCurrency = currency,
                    )
                }

                findOrThrow { workspaceRepository.findById(it) }.apply {
                    this.name = request.name
                    this.plan = request.plan
                    this.defaultCurrency = currency
                }
            }

            // Handle avatar upload and avatarUrl storage, if a file is provided
            avatar?.let {
                TODO()
            }

            workspaceRepository.save(entity).run {
                val id = requireNotNull(this.id) { "WorkspaceEntity must have a non-null id after save" }
                // Log the activity of creating an workspace
                activityService.log(
                    activity = riven.core.enums.activity.Activity.WORKSPACE,
                    operation = riven.core.enums.util.OperationType.CREATE,
                    userId = userId,
                    workspaceId = id,
                    entityType = ApplicationEntityType.WORKSPACE,
                    entityId = this.id,
                    "workspaceId" to id.toString(),
                    "name" to name
                )

                if (request.id == null) {
                    // Add the creator as the first member/owner of the workspace
                    WorkspaceMemberEntity(
                        workspaceId = id,
                        userId = userId,
                        role = WorkspaceRoles.OWNER
                    ).run {
                        workspaceMemberRepository.save(this)
                    }
                }

                // Publish analytics event after membership is persisted so memberCount is accurate
                val analyticsEvent = if (request.id == null) {
                    WorkspaceCreatedEvent(
                        workspaceId = id,
                        workspaceName = name,
                        createdAt = this.createdAt,
                        memberCount = 1,
                        userId = userId
                    )
                } else {
                    WorkspaceUpdatedEvent(
                        workspaceId = id,
                        workspaceName = name,
                        createdAt = this.createdAt,
                        memberCount = this.memberCount,
                        userId = userId
                    )
                }
                applicationEventPublisher.publishEvent(analyticsEvent)

                return this.toModel().also { workspace ->
                    userService.getUserWithWorkspacesFromSession().let {
                        // Membership array should be empty until transaction is over. Meaning we can determine if this is the first workspace made by the user
                        // Can also manually specify for the workspace to become the new default
                        if (it.memberships.isEmpty() || request.isDefault) {
                            it.apply {
                                defaultWorkspace = workspace
                            }.run {
                                userService.updateUserDetails(this)
                            }
                        }
                    }
                }
            }
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
                    activity = riven.core.enums.activity.Activity.WORKSPACE,
                    operation = riven.core.enums.util.OperationType.DELETE,
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
                    activity = riven.core.enums.activity.Activity.WORKSPACE_MEMBER,
                    operation = riven.core.enums.util.OperationType.DELETE,
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
                    activity = riven.core.enums.activity.Activity.WORKSPACE_MEMBER,
                    operation = riven.core.enums.util.OperationType.UPDATE,
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

