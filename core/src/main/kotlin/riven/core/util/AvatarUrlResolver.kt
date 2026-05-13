package cranium.core.util

import java.util.*

object AvatarUrlResolver {

    fun workspaceAvatarUrl(workspaceId: UUID, storageKey: String?): String? =
        storageKey?.trim()?.takeIf { it.isNotEmpty() }?.let { "/api/v1/avatars/workspace/$workspaceId" }

    fun userAvatarUrl(userId: UUID, storageKey: String?): String? =
        storageKey?.trim()?.takeIf { it.isNotEmpty() }?.let { "/api/v1/avatars/user/$userId" }
}
