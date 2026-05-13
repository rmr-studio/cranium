package cranium.core.models.identity

import java.util.UUID

/**
 * Output of the scoring step — a fully scored entity pair ready for suggestion persistence.
 *
 * [compositeScore] is the weighted aggregate of all [signals]. The persistence layer
 * converts [signals] via [MatchSignal.toMap] before writing the JSONB column.
 */
data class ScoredCandidate(
    val sourceEntityId: UUID,
    val targetEntityId: UUID,
    val compositeScore: Double,
    val signals: List<MatchSignal>,
)
