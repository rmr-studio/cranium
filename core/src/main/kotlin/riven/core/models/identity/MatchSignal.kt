package riven.core.models.identity

import riven.core.enums.identity.MatchSignalType
import riven.core.enums.identity.MatchSource

/**
 * Per-signal detail stored in the JSONB breakdown on [riven.core.entity.identity.MatchSuggestionEntity].
 *
 * Captures both sides of the comparison along with the raw similarity and the
 * weight that was applied for this signal type.
 *
 * [matchSource] indicates which query mechanism produced the candidate. Defaults to [MatchSource.TRIGRAM]
 * for backward compatibility with existing persisted JSONB records.
 *
 * [crossType] is true when the trigger attribute's signal type differs from the candidate's signal type,
 * indicating a cross-type match that may warrant a score discount.
 */
data class MatchSignal(
    val type: MatchSignalType,
    val sourceValue: String,
    val targetValue: String,
    val similarity: Double,
    val weight: Double,
    val matchSource: MatchSource = MatchSource.TRIGRAM,
    val crossType: Boolean = false,
) {
    /**
     * Serialises this signal to a plain map suitable for JSONB persistence.
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to type.name,
        "sourceValue" to sourceValue,
        "targetValue" to targetValue,
        "similarity" to similarity,
        "weight" to weight,
        "matchSource" to matchSource.name,
        "crossType" to crossType,
    )
}
