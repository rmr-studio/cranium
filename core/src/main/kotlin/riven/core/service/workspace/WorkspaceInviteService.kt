package riven.core.service.workspace

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.workspace.WorkspaceInviteEntity
import riven.core.entity.workspace.toModel
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.enums.workspace.WorkspaceInviteStatus
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ConflictException
import riven.core.models.workspace.WorkspaceInvite
import riven.core.repository.workspace.WorkspaceInviteRepository
import riven.core.repository.workspace.WorkspaceMemberRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.util.ServiceUtil.findManyResults
import riven.core.util.ServiceUtil.findOrThrow
import java.util.*


@Service
class WorkspaceInviteService(
    private val workspaceService: WorkspaceService,
    private val workspaceInviteRepository: WorkspaceInviteRepository,
    private val workspaceMemberRepository: WorkspaceMemberRepository,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService
) {

    /**
     * Create an invitation for a user to join an workspace with a specified role.
     *
     * @param workspaceId The UUID of the workspace to invite the user into.
     * @param email The invitee's email address.
     * @param role The role to assign to the invitee; must not be `OWNER` (use ownership transfer methods for that).
     * @return The created `WorkspaceInvite` model representing the persisted invitation.
     * @throws AccessDeniedException if the caller lacks workspace access or sufficient role.
     * @throws IllegalArgumentException if `role` is `OWNER` or a pending invite for the email already exists.
     * @throws ConflictException if the email already belongs to an existing workspace member.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    @Throws(AccessDeniedException::class, IllegalArgumentException::class)
    fun createWorkspaceInvitation(workspaceId: UUID, email: String, role: WorkspaceRoles): WorkspaceInvite {
        val userId = authTokenService.getUserId()
        return createWorkspaceInvitationInternal(workspaceId, email, role, userId)
    }

    /**
     * Create a workspace invitation without workspace access check. Used during onboarding
     * when the workspace role is not yet in the JWT.
     *
     * @param workspaceId the workspace to invite the user into
     * @param email the invitee's email address
     * @param role the role to assign to the invitee
     * @param invitedBy the UUID of the user sending the invitation
     */
    @Throws(IllegalArgumentException::class)
    internal fun createWorkspaceInvitationInternal(workspaceId: UUID, email: String, role: WorkspaceRoles, invitedBy: UUID): WorkspaceInvite {
        if (role == WorkspaceRoles.OWNER) {
            throw IllegalArgumentException("Cannot create an invite with the Owner role. Use transfer ownership methods instead.")
        }

        findManyResults {
            workspaceMemberRepository.findByWorkspaceId(workspaceId)
        }.run {
            if (this.any { it.user?.email == email }) {
                throw ConflictException("User with this email is already a member of the workspace.")
            }
        }

        workspaceInviteRepository.findByworkspaceIdAndEmailAndInviteStatus(
            workspaceId = workspaceId,
            email = email,
            inviteStatus = WorkspaceInviteStatus.PENDING
        ).run {
            if (this.isNotEmpty()) {
                throw IllegalArgumentException("An invitation for this email already exists.")
            }
        }

        val entity = workspaceInviteRepository.save(
            WorkspaceInviteEntity(
                workspaceId = workspaceId,
                email = email,
                role = role,
                inviteStatus = WorkspaceInviteStatus.PENDING,
                invitedBy = invitedBy,
            )
        )

        activityService.log(
            activity = Activity.WORKSPACE_MEMBER_INVITE,
            operation = OperationType.CREATE,
            userId = invitedBy,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.WORKSPACE,
            entityId = workspaceId,
            "inviteId" to entity.id.toString(),
            "email" to email,
            "role" to role.toString()
        )

        return entity.toModel()
    }

    /**
     * Handles a user's response to an workspace invitation identified by its token.
     *
     * Validates that the authenticated user's email matches the invitation email and that the
     * invitation is in the PENDING state, then updates the invitation status to ACCEPTED or
     * DECLINED. If accepted, the authenticated user is added to the workspace with the
     * invitation's role.
     *
     * @param token The invitation token used to locate the invitation.
     * @param accepted `true` to accept the invitation, `false` to decline it.
     * @throws AccessDeniedException if the authenticated user's email does not match the invite email.
     * @throws IllegalArgumentException if the invitation is not in the PENDING state.
     */
    @Throws(AccessDeniedException::class, IllegalArgumentException::class)
    @Transactional
    fun handleInvitationResponse(token: String, accepted: Boolean) {
        findOrThrow { workspaceInviteRepository.findByToken(token) }.let { invitation ->
            // Assert the user is the one who was invited
            authTokenService.getUserEmail().let {
                if (it != invitation.email) {
                    throw AccessDeniedException("User email does not match the invite email.")
                }
            }

            if (invitation.inviteStatus != WorkspaceInviteStatus.PENDING) {
                throw IllegalArgumentException("Cannot respond to an invitation that is not pending.")
            }

            // Handle invitation acceptance - Add user as a member of an workspace
            if (accepted) {
                invitation.apply {
                    inviteStatus = WorkspaceInviteStatus.ACCEPTED
                }.run {
                    workspaceInviteRepository.save(this)
                    // Add the user to the workspace as a member
                    workspaceService.addMemberToWorkspace(
                        workspaceId = invitation.workspaceId,
                        userId = authTokenService.getUserId(),
                        role = invitation.role
                    )
                    // TODO: Send out acceptance email
                    return
                }
            }

            // Handle invitation rejection - Update the invite status to DECLINED
            invitation.apply {
                inviteStatus = WorkspaceInviteStatus.DECLINED
            }.run {
                workspaceInviteRepository.save(this)
                // TODO: Send out rejection email
                return
            }
        }
    }

    /**
     * Returns the workspace invites addressed to the authenticated user.
     *
     * @return A list of `WorkspaceInvite` models for the email extracted from the current user's auth token (empty if none).
     */
    fun getUserInvites(): List<WorkspaceInvite> {
        authTokenService.getUserEmail().let { email ->
            findManyResults { workspaceInviteRepository.findByEmail(email) }.run {
                return this.map { it.toModel() }
            }
        }
    }

    /**
     * Retrieves all workspace invitations for the specified workspace.
     *
     * @param workspaceId ID of the workspace whose invites are returned.
     * @return A list of WorkspaceInvite models for the workspace; empty if none exist.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getWorkspaceInvites(workspaceId: UUID): List<WorkspaceInvite> {
        // Fetch all invites for the workspace
        return findManyResults { workspaceInviteRepository.findByworkspaceId(workspaceId) }
            .map { it.toModel() }
    }

    /**
     * Revoke a pending workspace invitation.
     *
     * Deletes the invitation identified by [id] for the given workspace only if its status is PENDING.
     *
     * @param workspaceId The workspace's UUID the invitation belongs to.
     * @param id The UUID of the invitation to revoke.
     * @throws IllegalArgumentException if the invitation exists but is not in the PENDING state.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId) and @workspaceSecurity.hasWorkspaceRoleOrHigher(#workspaceId, 'ADMIN')")
    fun revokeWorkspaceInvite(workspaceId: UUID, id: UUID) {
        // Find the invite by ID
        findOrThrow { workspaceInviteRepository.findById(id) }.let { invite ->
            // Ensure the invite is still pending
            if (invite.inviteStatus != WorkspaceInviteStatus.PENDING) {
                throw IllegalArgumentException("Cannot revoke an invitation that is not pending.")
            }

            // Delete invitation
            workspaceInviteRepository.deleteById(id)
        }
    }

}