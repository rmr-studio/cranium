package cranium.core.models.response.identity

import java.time.ZonedDateTime
import java.util.UUID

/**
 * API response shape for a cluster detail view — metadata plus enriched member list.
 */
data class ClusterDetailResponse(
    val id: UUID,
    val workspaceId: UUID,
    val name: String?,
    val memberCount: Int,
    val members: List<ClusterMemberContext>,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
)
