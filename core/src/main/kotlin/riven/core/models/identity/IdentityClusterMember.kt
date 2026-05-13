package cranium.core.models.identity

import java.time.ZonedDateTime
import java.util.UUID

/**
 * Domain model for an identity cluster member — a single entity belonging to a cluster.
 */
data class IdentityClusterMember(
    val id: UUID,
    val clusterId: UUID,
    val entityId: UUID,
    val joinedAt: ZonedDateTime,
    val joinedBy: UUID?,
)
