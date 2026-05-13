package cranium.core.models.integration.mapping

import cranium.core.enums.common.validation.SchemaType

/**
 * A fully resolved field mapping combining the source path, transform strategy,
 * and target schema type. Produced by the caller (e.g. sync pipeline) from
 * catalog field mapping definitions and entity type attribute metadata.
 *
 * @param sourcePath dot-separated path into the external payload (e.g. "address.city")
 * @param transform the transformation to apply to the extracted value
 * @param targetSchemaType the SchemaType of the target entity attribute
 */
data class ResolvedFieldMapping(
    val sourcePath: String,
    val transform: FieldTransform = FieldTransform.Direct,
    val targetSchemaType: SchemaType
)
