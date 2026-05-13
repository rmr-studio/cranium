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
import cranium.core.models.core.base.BillingEventBase

/**
 * DTC Billing Event — a financial event in the ecommerce transaction lifecycle.
 * Includes purchase and shipping-fee event types specific to direct-to-consumer commerce.
 */
object DtcBillingEventModel : cranium.core.models.core.CoreModelDefinition(
    key = "billing-event",
    displayNameSingular = "Billing Event",
    displayNamePlural = "Billing Events",
    iconType = IconType.CREDIT_CARD,
    iconColour = IconColour.GREEN,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.BILLING,
    role = EntityTypeRole.CATALOG,
    identifierKey = "description",
    semanticDefinition = "A financial event in the ecommerce transaction lifecycle — purchases, refunds, credits, shipping fees, or adjustments.",
    semanticTags = listOf("billing", "finance", "revenue", "ecommerce"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.BILLING,
            semanticGroup = SemanticGroup.FINANCIAL,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = BillingEventBase.attributes + mapOf(
        "type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Type", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("charge", "refund", "credit", "adjustment", "shipping-fee")),
            semantics = AttributeSemantics(
                definition = "The type of billing event in the ecommerce transaction flow.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("billing", "classification"),
            ),
        ),
    ),
)
