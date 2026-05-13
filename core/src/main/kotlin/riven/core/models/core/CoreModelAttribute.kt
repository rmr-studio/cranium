package cranium.core.models.core

import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.models.common.validation.SchemaOptions

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
    val options: SchemaOptions? = null,
    val semantics: cranium.core.models.core.AttributeSemantics? = null,
)

data class AttributeSemantics(
    val definition: String,
    val classification: SemanticAttributeClassification? = null,
    val tags: List<String> = emptyList(),
)
