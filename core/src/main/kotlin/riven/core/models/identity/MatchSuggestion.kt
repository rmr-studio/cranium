package riven.core.models.identity

import riven.core.enums.identity.MatchSuggestionStatus
import riven.core.models.common.json.JsonObject
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Domain model for a match suggestion — a candidate entity pair for human review.
 */
data class MatchSuggestion(
    val id: UUID,
    val workspaceId: UUID,
    val sourceEntityId: UUID,
    val targetEntityId: UUID,
    val status: MatchSuggestionStatus,
    val confidenceScore: BigDecimal,
    val signals: List<Map<String, Any?>>,
    val rejectionSignals: JsonObject?,
    val resolvedBy: UUID?,
    val resolvedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
)
