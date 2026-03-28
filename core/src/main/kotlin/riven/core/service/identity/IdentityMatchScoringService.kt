package riven.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.identity.MatchSignalType
import riven.core.models.identity.CandidateMatch
import riven.core.models.identity.MatchSignal
import riven.core.models.identity.ScoredCandidate
import java.util.UUID

/**
 * Pure computation service that scores candidate entity pairs against a trigger entity.
 *
 * No database access — all inputs are passed explicitly. Called from the Temporal
 * ScoreCandidates activity after [IdentityMatchCandidateService.findCandidates] has
 * returned the candidate list.
 */
@Service
class IdentityMatchScoringService(
    private val logger: KLogger,
) {

    companion object {
        /** Candidates whose composite score falls below this value are not returned. */
        const val MINIMUM_SCORE_THRESHOLD = 0.5
    }

    // ------ Public operations ------

    /**
     * Scores a list of raw candidate matches against the trigger entity's attribute values.
     *
     * For each candidate entity:
     * 1. Selects the best-matching attribute per signal type (highest similarity).
     * 2. Builds a [MatchSignal] for each type with similarity > 0.
     * 3. Computes a weighted average composite score.
     * 4. Returns only candidates whose composite score >= [MINIMUM_SCORE_THRESHOLD].
     *
     * @param triggerEntityId the source entity being matched
     * @param triggerAttributes map of signal type to normalized attribute value for the trigger entity
     * @param candidates raw candidate matches from [IdentityMatchCandidateService.findCandidates]
     * @return list of [ScoredCandidate] instances above the threshold, each with a signal breakdown
     */
    fun scoreCandidates(
        triggerEntityId: UUID,
        triggerAttributes: Map<MatchSignalType, String>,
        candidates: List<CandidateMatch>,
    ): List<ScoredCandidate> {
        val grouped = candidates.groupBy { it.candidateEntityId }
        logger.debug { "Scoring ${grouped.size} candidate entities from ${candidates.size} raw candidate rows" }

        return grouped.mapNotNull { (candidateEntityId, candidateRows) ->
            scoreCandidate(triggerEntityId, candidateEntityId, triggerAttributes, candidateRows)
        }
    }

    // ------ Private helpers ------

    /**
     * Scores a single candidate entity and returns a [ScoredCandidate] if the score meets the threshold,
     * or null if the candidate should be filtered out.
     */
    private fun scoreCandidate(
        triggerEntityId: UUID,
        candidateEntityId: UUID,
        triggerAttributes: Map<MatchSignalType, String>,
        candidateRows: List<CandidateMatch>,
    ): ScoredCandidate? {
        val signals = buildSignals(triggerAttributes, candidateRows)
        if (signals.isEmpty()) return null

        val compositeScore = computeCompositeScore(signals)
        if (compositeScore < MINIMUM_SCORE_THRESHOLD) {
            logger.debug { "Candidate $candidateEntityId score $compositeScore below threshold — skipping" }
            return null
        }

        return ScoredCandidate(
            sourceEntityId = triggerEntityId,
            targetEntityId = candidateEntityId,
            compositeScore = compositeScore,
            signals = signals,
        )
    }

    /**
     * Builds the [MatchSignal] list for a candidate entity.
     *
     * Groups candidate rows by signal type, takes the best match per type (highest similarity),
     * and creates a [MatchSignal] for each with similarity > 0.
     */
    private fun buildSignals(
        triggerAttributes: Map<MatchSignalType, String>,
        candidateRows: List<CandidateMatch>,
    ): List<MatchSignal> {
        return candidateRows
            .groupBy { it.signalType }
            .mapNotNull { (signalType, rowsForType) ->
                val best = rowsForType.maxByOrNull { it.similarityScore } ?: return@mapNotNull null
                if (best.similarityScore == 0.0) return@mapNotNull null

                val sourceValue = triggerAttributes[signalType] ?: best.candidateValue
                val weight = MatchSignalType.DEFAULT_WEIGHTS[signalType]
                    ?: error("No default weight defined for signal type $signalType")

                MatchSignal(
                    type = signalType,
                    sourceValue = sourceValue,
                    targetValue = best.candidateValue,
                    similarity = best.similarityScore,
                    weight = weight,
                )
            }
    }

    /**
     * Computes the weighted average composite score across a list of signals.
     *
     * Formula: Sum(similarity * weight) / Sum(weight)
     * Result is guaranteed to be in the range [0.0, 1.0].
     */
    private fun computeCompositeScore(signals: List<MatchSignal>): Double {
        val numerator = signals.sumOf { it.similarity * it.weight }
        val denominator = signals.sumOf { it.weight }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
}
