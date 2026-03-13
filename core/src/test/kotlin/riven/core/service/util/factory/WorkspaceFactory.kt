package riven.core.service.util.factory

import riven.core.entity.user.UserEntity
import riven.core.entity.workspace.WorkspaceEntity
import riven.core.entity.workspace.WorkspaceInviteEntity
import riven.core.entity.workspace.WorkspaceMemberEntity
import riven.core.enums.workspace.WorkspaceInviteStatus
import riven.core.enums.workspace.WorkspaceRoles
import java.util.*

object WorkspaceFactory {

    fun createWorkspace(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Workspace",
    ) = WorkspaceEntity(
        id = id,
        name = name,
    )

    fun createWorkspaceMember(
        user: UserEntity,
        workspaceId: UUID,
        role: WorkspaceRoles = WorkspaceRoles.MEMBER,
    ): WorkspaceMemberEntity {
        val userId = requireNotNull(user.id) { "User ID must not be null" }
        return WorkspaceMemberEntity(
            workspaceId = workspaceId,
            userId = userId,
            role = role,
        ).apply {
            this.user = user
        }
    }

    fun createWorkspaceInvite(
        email: String,
        workspaceId: UUID,
        role: WorkspaceRoles = WorkspaceRoles.MEMBER,
        token: String = WorkspaceInviteEntity.generateSecureToken(),
        invitedBy: UUID = UUID.randomUUID(),
        status: WorkspaceInviteStatus = WorkspaceInviteStatus.PENDING,
    ) = WorkspaceInviteEntity(
        id = UUID.randomUUID(),
        email = email,
        workspaceId = workspaceId,
        role = role,
        token = token,
        inviteStatus = status,
        invitedBy = invitedBy,
    )
}
