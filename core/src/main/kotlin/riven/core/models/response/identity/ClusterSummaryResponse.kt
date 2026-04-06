package riven.core.models.response.identity

import java.time.ZonedDateTime
import java.util.UUID

/**
 * API response shape for a cluster list item — name and member count summary.
 */
data class ClusterSummaryResponse(
    val id: UUID,
    val workspaceId: UUID,
    val name: String?,
    val memberCount: Int,
    val createdAt: ZonedDateTime,
)
