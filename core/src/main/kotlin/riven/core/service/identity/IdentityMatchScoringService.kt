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

        /**
         * Minimum base signal weight required for a single-signal candidate to pass the confidence gate.
         * Candidates with a single signal whose base weight is below this threshold are rejected.
         * Candidates with 2 or more distinct signals automatically pass regardless of individual weights.
         */
        const val CONFIDENCE_GATE_THRESHOLD = 0.85

        /**
         * Multiplier applied to a signal's base weight when the candidate attribute's signal type
         * differs from the trigger signal type (cross-type match). A value matched against the wrong
         * attribute type (e.g. an email string stored in a NAME attribute) is penalised.
         */
        const val CROSS_TYPE_DISCOUNT = 0.5
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

        if (!passesConfidenceGate(candidateEntityId, signals)) return null

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
     * and creates a [MatchSignal] for each with similarity > 0. Applies a [CROSS_TYPE_DISCOUNT]
     * when the candidate attribute's signal type differs from the trigger signal type.
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
                val baseWeight = MatchSignalType.DEFAULT_WEIGHTS[signalType]
                    ?: error("No default weight defined for signal type $signalType")

                val isCrossType = best.candidateSignalType != null && best.candidateSignalType != signalType
                val effectiveWeight = if (isCrossType) baseWeight * CROSS_TYPE_DISCOUNT else baseWeight

                MatchSignal(
                    type = signalType,
                    sourceValue = sourceValue,
                    targetValue = best.candidateValue,
                    similarity = best.similarityScore,
                    weight = effectiveWeight,
                    matchSource = best.matchSource,
                    crossType = isCrossType,
                )
            }
    }

    /**
     * Applies the confidence gate to a candidate's signal list.
     *
     * A candidate with 2 or more distinct signals automatically passes.
     * A single-signal candidate passes only if its base weight (from [MatchSignalType.DEFAULT_WEIGHTS])
     * meets or exceeds [CONFIDENCE_GATE_THRESHOLD]. The gate always checks the base weight, never the
     * effective (discounted) weight, so a high-weight signal type is not penalised for a cross-type match.
     *
     * @param candidateEntityId used for debug logging when rejecting a candidate
     * @param signals the signals built by [buildSignals] for this candidate
     * @return true if the candidate passes the gate; false if it should be filtered out
     */
    private fun passesConfidenceGate(candidateEntityId: UUID, signals: List<MatchSignal>): Boolean {
        if (signals.size >= 2) return true

        val signal = signals.first()
        val baseWeight = MatchSignalType.DEFAULT_WEIGHTS[signal.type]
            ?: error("No default weight defined for signal type ${signal.type}")

        if (baseWeight < CONFIDENCE_GATE_THRESHOLD) {
            logger.debug {
                "Candidate $candidateEntityId rejected by confidence gate: " +
                    "single ${signal.type} signal base weight $baseWeight < $CONFIDENCE_GATE_THRESHOLD"
            }
            return false
        }
        return true
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
