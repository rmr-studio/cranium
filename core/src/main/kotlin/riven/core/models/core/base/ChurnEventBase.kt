package cranium.core.models.core.base

import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.core.DynamicDefaultFunction
import cranium.core.models.common.validation.DefaultValue
import cranium.core.models.common.validation.SchemaOptions
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute

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
            options = SchemaOptions(
                enum = listOf("voluntary", "involuntary"),
                defaultValue = DefaultValue.Static("voluntary")
            ),
            semantics = AttributeSemantics(
                definition = "Whether the customer chose to cancel (voluntary) or was lost due to payment failure etc. (involuntary).",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("churn-type", "classification"),
            ),
        )
    )
}
