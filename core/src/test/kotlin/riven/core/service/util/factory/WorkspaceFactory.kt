package cranium.core.service.util.factory

import cranium.core.entity.user.UserEntity
import cranium.core.entity.workspace.WorkspaceEntity
import cranium.core.entity.workspace.WorkspaceInviteEntity
import cranium.core.entity.workspace.WorkspaceMemberEntity
import cranium.core.enums.workspace.WorkspaceInviteStatus
import cranium.core.enums.workspace.WorkspaceRoles
import java.util.*

object WorkspaceFactory {

    fun createWorkspace(
        id: UUID = UUID.randomUUID(),
        name: String = "Test Workspace",
        connotationEnabled: Boolean = false,
    ) = WorkspaceEntity(
        id = id,
        name = name,
        connotationEnabled = connotationEnabled,
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
