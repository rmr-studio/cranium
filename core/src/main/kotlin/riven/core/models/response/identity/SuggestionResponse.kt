package cranium.core.models.response.identity

import cranium.core.enums.identity.MatchSuggestionStatus
import cranium.core.models.identity.MatchSuggestion
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.UUID

/**
 * API response shape for a match suggestion.
 *
 * Note: rejectionSignals is intentionally excluded — it is internal state
 * used only for re-suggestion context and is not part of the public API contract.
 */
data class SuggestionResponse(
    val id: UUID,
    val workspaceId: UUID,
    val sourceEntityId: UUID,
    val targetEntityId: UUID,
    val status: MatchSuggestionStatus,
    val confidenceScore: BigDecimal,
    val signals: List<Map<String, Any?>>,
    val resolvedBy: UUID?,
    val resolvedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        /** Maps a [MatchSuggestion] domain model to this API response. */
        fun from(model: MatchSuggestion): SuggestionResponse = SuggestionResponse(
            id = model.id,
            workspaceId = model.workspaceId,
            sourceEntityId = model.sourceEntityId,
            targetEntityId = model.targetEntityId,
            status = model.status,
            confidenceScore = model.confidenceScore,
            signals = model.signals,
            resolvedBy = model.resolvedBy,
            resolvedAt = model.resolvedAt,
            createdAt = model.createdAt,
            updatedAt = model.updatedAt,
        )
    }
}
