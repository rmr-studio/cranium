package riven.core.enums.identity

import com.fasterxml.jackson.annotation.JsonProperty
import riven.core.enums.common.validation.SchemaType

/**
 * The category of attribute that produced a match signal.
 *
 * Each value carries a default weight used by the scoring service when no
 * workspace-specific override is configured.
 */
enum class MatchSignalType {
    @JsonProperty("EMAIL")
    EMAIL,

    @JsonProperty("PHONE")
    PHONE,

    @JsonProperty("NAME")
    NAME,

    @JsonProperty("COMPANY")
    COMPANY,

    @JsonProperty("CUSTOM_IDENTIFIER")
    CUSTOM_IDENTIFIER;

    companion object {
        /** Default weights keyed by signal type. Used by IdentityMatchScoringService. */
        val DEFAULT_WEIGHTS: Map<MatchSignalType, Double> = mapOf(
            EMAIL to 0.9,
            PHONE to 0.85,
            NAME to 0.5,
            COMPANY to 0.3,
            CUSTOM_IDENTIFIER to 0.7,
        )

        /**
         * Maps a [SchemaType] to the closest [MatchSignalType].
         *
         * EMAIL and PHONE map directly. All other schema types fall through to
         * [CUSTOM_IDENTIFIER] — NAME and COMPANY are contextual derivations handled by
         * the scoring service using attribute-name heuristics.
         */
        fun fromSchemaType(schemaType: SchemaType): MatchSignalType = when (schemaType) {
            SchemaType.EMAIL -> EMAIL
            SchemaType.PHONE -> PHONE
            else -> CUSTOM_IDENTIFIER
        }

        /**
         * Maps the TEXT value stored in the `signal_type` column on
         * `entity_type_semantic_metadata` to a [MatchSignalType].
         *
         * Returns null when [value] is null (pre-existing rows without a signal_type).
         * Null callers should fall back to [fromSchemaType] or [CUSTOM_IDENTIFIER].
         *
         * Note: The column stores "CUSTOM" but the enum value is [CUSTOM_IDENTIFIER] —
         * never use [valueOf] to parse this column.
         */
        fun fromColumnValue(value: String?): MatchSignalType? = when (value) {
            "EMAIL" -> EMAIL
            "PHONE" -> PHONE
            "NAME" -> NAME
            "COMPANY" -> COMPANY
            "CUSTOM" -> CUSTOM_IDENTIFIER
            null -> null
            else -> throw IllegalArgumentException("Unrecognized signal_type value: '$value'")
        }
    }
}
