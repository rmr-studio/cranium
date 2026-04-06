package riven.core.models.identity

import riven.core.enums.identity.MatchSignalType
import riven.core.enums.identity.MatchSource
import java.util.UUID

/**
 * Raw candidate produced by the pg_trgm similarity query.
 *
 * One instance per matching attribute row returned from the candidate scan.
 * Multiple [CandidateMatch] instances for the same [candidateEntityId] will be
 * aggregated by the scoring service into a [ScoredCandidate].
 *
 * [candidateSignalType] carries the signal type from the candidate's semantic metadata
 * row (sm.signal_type). Null means no signal type was stored on the metadata, which
 * is treated as "no cross-type discount" by the scoring service.
 *
 * [matchSource] indicates which query mechanism produced this row. Defaults to [MatchSource.TRIGRAM]
 * for backward compatibility with Temporal activity serialization boundaries.
 */
data class CandidateMatch(
    val candidateEntityId: UUID,
    val candidateAttributeId: UUID,
    val candidateValue: String,
    val signalType: MatchSignalType,
    val similarityScore: Double,
    val candidateSignalType: MatchSignalType? = null,
    val matchSource: MatchSource = MatchSource.TRIGRAM,
)
