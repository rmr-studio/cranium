package riven.core.enums.connotation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Names of the polymorphic metadata categories on [riven.core.models.connotation.ConnotationMetadata].
 *
 * The [name] of each enum value matches the JSON key persisted in `entity_connotation.connotation_metadata`
 * (UPPERCASE — see `@JsonProperty` on [riven.core.models.connotation.ConnotationMetadata]). When passing
 * a name to JSONB-path queries, use `metadataType.name` directly.
 */
enum class ConnotationMetadataType {
    @JsonProperty("SENTIMENT") SENTIMENT,
    @JsonProperty("RELATIONAL") RELATIONAL,
    @JsonProperty("STRUCTURAL") STRUCTURAL,
}
