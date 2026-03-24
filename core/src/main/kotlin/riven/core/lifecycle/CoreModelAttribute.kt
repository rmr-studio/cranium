package riven.core.lifecycle

import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.semantics.SemanticAttributeClassification

/**
 * Attribute definition within a core model. String-keyed (converted to UUID during installation).
 *
 * Mirrors the JSON manifest attribute schema but as a type-safe Kotlin data class.
 * All core model attributes are protected=true (immutable after installation).
 */
data class CoreModelAttribute(
    val schemaType: SchemaType,
    val label: String,
    val dataType: DataType,
    val format: String? = null,
    val required: Boolean = false,
    val unique: Boolean = false,
    val options: AttributeOptions? = null,
    val semantics: AttributeSemantics? = null,
)

data class AttributeOptions(
    val default: Any? = null,
    val prefix: String? = null,
    val regex: String? = null,
    val enum: List<String>? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
)

data class AttributeSemantics(
    val definition: String,
    val classification: SemanticAttributeClassification? = null,
    val tags: List<String> = emptyList(),
)
