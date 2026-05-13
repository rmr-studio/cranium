package cranium.core.projection.user

import cranium.core.enums.workspace.WorkspaceDisplay
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.user.UserDisplay
import cranium.core.models.workspace.WorkspaceMember
import cranium.core.util.AvatarUrlResolver
import java.time.Instant
import java.time.ZoneId
import java.util.*

/**
 * Projection interface for workspace membership data from native query.
 * Spring Data JPA automatically maps query columns to these getter methods.
 *
 * Used to fetch user workspace memberships in a single optimized query with JOIN.
 *
 * Note: Native queries return java.time.Instant for TIMESTAMP WITH TIME ZONE columns,
 * so getMemberSince() returns Instant which is converted to ZonedDateTime in the extension function.
 */
interface UserWorkspaceMembershipProjection {
    fun getUserId(): UUID
    fun getUserName(): String
    fun getUserEmail(): String
    fun getUserAvatarUrl(): String?
    fun getWorkspaceId(): UUID
    fun getWorkspaceName(): String
    fun getWorkspaceAvatarUrl(): String?
    fun getRole(): String
    fun getMemberSince(): Instant
}

/**
 * Extension function to convert projection to WorkspaceMember domain model.
 */
fun UserWorkspaceMembershipProjection.toWorkspaceMember(): WorkspaceMember {
    return WorkspaceMember(
        workspace = WorkspaceDisplay(
            id = getWorkspaceId(),
            name = getWorkspaceName(),
            avatarUrl = AvatarUrlResolver.workspaceAvatarUrl(getWorkspaceId(), getWorkspaceAvatarUrl())
        ),
        user = UserDisplay(
            id = getUserId(),
            email = getUserEmail(),
            name = getUserName(),
            avatarUrl = AvatarUrlResolver.userAvatarUrl(getUserId(), getUserAvatarUrl())
        ),
        role = WorkspaceRoles.valueOf(getRole()),
        memberSince = getMemberSince().atZone(ZoneId.systemDefault())
    )
}
