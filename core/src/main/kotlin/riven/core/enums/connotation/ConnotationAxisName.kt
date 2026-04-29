package riven.core.enums.connotation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Names of the polymorphic axes on the [riven.core.models.connotation.ConnotationMetadataEnvelope].
 *
 * The [name] of each enum value matches the JSON key persisted in `entity_connotation.connotation_metadata`
 * (UPPERCASE — see `@JsonProperty` on [riven.core.models.connotation.ConnotationAxes]). When passing
 * an axis name to JSONB-path queries, use `axis.name` directly.
 */
enum class ConnotationAxisName {
    @JsonProperty("SENTIMENT") SENTIMENT,
    @JsonProperty("RELATIONAL") RELATIONAL,
    @JsonProperty("STRUCTURAL") STRUCTURAL,
}
