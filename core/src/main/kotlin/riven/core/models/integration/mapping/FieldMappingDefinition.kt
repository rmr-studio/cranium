package riven.core.models.integration.mapping

/**
 * Represents a single field mapping entry deserialized from the integration manifest JSONB.
 *
 * Maps one external provider field (identified by [source] path) to a target entity attribute,
 * with an optional [transform] specifying how the value should be converted.
 */
data class FieldMappingDefinition(
    val source: String,
    val transform: FieldTransform = FieldTransform.Direct
)
