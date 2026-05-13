package cranium.core.models.workspace


import cranium.core.enums.workspace.WorkspaceDisplay
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.user.UserDisplay
import java.time.ZonedDateTime

data class WorkspaceMember(
    val workspace: WorkspaceDisplay,
    val user: UserDisplay,
    val role: WorkspaceRoles,
    val memberSince: ZonedDateTime,
)

