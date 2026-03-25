package riven.core.lifecycle.models.dtc

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.lifecycle.AttributeOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition
import riven.core.lifecycle.models.base.ChurnEventBase

/**
 * DTC Churn Event — records when and why a DTC customer relationship ended.
 * Includes ecommerce-specific churn reasons and revenue-lost tracking.
 */
object DtcChurnEventModel : CoreModelDefinition(
    key = "churn-event",
    displayNameSingular = "Churn Event",
    displayNamePlural = "Churn Events",
    iconType = IconType.USER_MINUS,
    iconColour = IconColour.RED,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.RETENTION,
    identifierKey = "reason",
    semanticDefinition = "Records when and why a customer stopped purchasing. The terminal lifecycle event for DTC ecommerce, with revenue impact tracking.",
    semanticTags = listOf("churn", "retention", "lifecycle", "revenue"),
    attributes = ChurnEventBase.attributes + mapOf(
        "reason" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Reason", dataType = DataType.STRING,
            required = true,
            options = AttributeOptions(enum = listOf("price", "competitor", "no-longer-needed", "poor-experience", "product-quality", "shipping-issues", "sizing-issues", "unknown")),
            semantics = AttributeSemantics(
                definition = "The stated or inferred reason for the customer stopping purchases.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("churn-reason", "analysis"),
            ),
        ),
        "revenue-lost" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Revenue Lost", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Estimated revenue lost from this customer churning.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("revenue-impact", "financial"),
            ),
        ),
    ),
)
