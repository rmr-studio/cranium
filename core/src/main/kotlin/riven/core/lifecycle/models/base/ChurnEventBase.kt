package riven.core.lifecycle.models.base

import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.models.common.validation.DefaultValue
import riven.core.models.common.validation.SchemaOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute

/**
 * Shared attributes for the Churn Event model across all business types.
 * The `reason` enum and revenue-impact field differ per business type.
 */
object ChurnEventBase {

    val attributes = mapOf(
        "date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Date", dataType = DataType.STRING,
            format = "date", required = true,
            options = SchemaOptions(defaultValue = DefaultValue.Dynamic(DynamicDefaultFunction.CURRENT_DATE)),
            semantics = AttributeSemantics(
                definition = "Date the churn event occurred.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "churn"),
            ),
        ),
        "type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Type", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("voluntary", "involuntary"), defaultValue = DefaultValue.Static("voluntary")),
            semantics = AttributeSemantics(
                definition = "Whether the customer chose to cancel (voluntary) or was lost due to payment failure etc. (involuntary).",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("churn-type", "classification"),
            ),
        )
    )
}
