package cranium.core.models.core.models.dtc

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.models.common.validation.SchemaOptions
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.ProjectionAcceptRule
import cranium.core.models.core.base.ChurnEventBase

/**
 * DTC Churn Event — records when and why a DTC customer relationship ended.
 * Includes ecommerce-specific churn reasons and revenue-lost tracking.
 */
object DtcChurnEventModel : cranium.core.models.core.CoreModelDefinition(
    key = "churn-event",
    displayNameSingular = "Churn Event",
    displayNamePlural = "Churn Events",
    iconType = IconType.USER_MINUS,
    iconColour = IconColour.RED,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.RETENTION,
    role = EntityTypeRole.CATALOG,
    identifierKey = "reason",
    semanticDefinition = "Records when and why a customer stopped purchasing. The terminal lifecycle event for DTC ecommerce, with revenue impact tracking.",
    semanticTags = listOf("churn", "retention", "lifecycle", "revenue"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.RETENTION,
            semanticGroup = SemanticGroup.FINANCIAL,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = ChurnEventBase.attributes + mapOf(
        "reason" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Reason", dataType = DataType.STRING,
            required = true,
            options = SchemaOptions(
                enum = listOf(
                    "price",
                    "competitor",
                    "no-longer-needed",
                    "poor-experience",
                    "product-quality",
                    "shipping-issues",
                    "sizing-issues",
                    "unknown"
                )
            ),
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
