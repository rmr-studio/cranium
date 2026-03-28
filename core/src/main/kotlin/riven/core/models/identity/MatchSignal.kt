package riven.core.models.identity

import riven.core.enums.identity.MatchSignalType

/**
 * Per-signal detail stored in the JSONB breakdown on [riven.core.entity.identity.MatchSuggestionEntity].
 *
 * Captures both sides of the comparison along with the raw similarity and the
 * weight that was applied for this signal type.
 */
data class MatchSignal(
    val type: MatchSignalType,
    val sourceValue: String,
    val targetValue: String,
    val similarity: Double,
    val weight: Double,
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
    )
}
