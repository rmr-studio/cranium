package cranium.core.models.response.identity

import cranium.core.enums.integration.SourceType
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Enriched cluster member context, combining membership metadata with entity fields.
 *
 * Fields typeKey, sourceType, and identifierKey are nullable because the referenced entity
 * may have been soft-deleted after joining the cluster.
 */
data class ClusterMemberContext(
    val entityId: UUID,
    val typeKey: String?,
    val sourceType: SourceType?,
    val identifierKey: UUID?,
    val joinedAt: ZonedDateTime,
)
