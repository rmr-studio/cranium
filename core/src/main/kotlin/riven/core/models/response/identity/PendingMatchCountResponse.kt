package riven.core.models.response.identity

import java.util.UUID

/**
 * API response for pending match count — how many PENDING suggestions involve the given entity.
 */
data class PendingMatchCountResponse(
    val entityId: UUID,
    val pendingCount: Long,
)
