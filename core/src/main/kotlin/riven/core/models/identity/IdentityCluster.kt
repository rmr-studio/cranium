package riven.core.models.identity

import java.time.ZonedDateTime
import java.util.UUID

/**
 * Domain model for an identity cluster — a group of entities confirmed as the same real-world identity.
 */
data class IdentityCluster(
    val id: UUID,
    val workspaceId: UUID,
    val name: String?,
    val memberCount: Int,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
)
