package cranium.core.models.workspace

import cranium.core.enums.workspace.WorkspaceInviteStatus
import cranium.core.enums.workspace.WorkspaceRoles
import java.time.ZonedDateTime
import java.util.*

data class WorkspaceInvite(
    val id: UUID,
    val workspaceId: UUID,
    val email: String,
    val inviteToken: String,
    val invitedBy: UUID? = null,
    val createdAt: ZonedDateTime,
    val expiresAt: ZonedDateTime,
    val role: WorkspaceRoles,
    val status: WorkspaceInviteStatus
)
