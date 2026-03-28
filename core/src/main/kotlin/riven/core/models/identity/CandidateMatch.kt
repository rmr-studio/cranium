package riven.core.models.identity

import riven.core.enums.identity.MatchSignalType
import java.util.UUID

/**
 * Raw candidate produced by the pg_trgm similarity query.
 *
 * One instance per matching attribute row returned from the candidate scan.
 * Multiple [CandidateMatch] instances for the same [candidateEntityId] will be
 * aggregated by the scoring service into a [ScoredCandidate].
 */
data class CandidateMatch(
    val candidateEntityId: UUID,
    val candidateAttributeId: UUID,
    val candidateValue: String,
    val signalType: MatchSignalType,
    val similarityScore: Double,
)
