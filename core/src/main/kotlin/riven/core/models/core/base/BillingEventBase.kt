package riven.core.models.core.base

import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.models.common.validation.DefaultValue
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute

/**
 * Shared attributes for the Billing Event model across all business types.
 * The `type` enum differs per business type and is defined in each variant.
 */
object BillingEventBase {

    val attributes = mapOf(
        "description" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Description", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Description of the billing event.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name"),
            ),
        ),
        "amount" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Amount", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Monetary value of this billing event.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("financial", "revenue"),
            ),
        ),
        "date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Date", dataType = DataType.STRING,
            format = "date",
            options = SchemaOptions(defaultValue = DefaultValue.Dynamic(DynamicDefaultFunction.CURRENT_DATE)),
            semantics = AttributeSemantics(
                definition = "Date the billing event occurred.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "billing"),
            ),
        ),
    )
}
