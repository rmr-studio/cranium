package riven.core.models.core.models.dtc

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityTypeRole
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule
import riven.core.models.core.models.base.BillingEventBase

/**
 * DTC Billing Event — a financial event in the ecommerce transaction lifecycle.
 * Includes purchase and shipping-fee event types specific to direct-to-consumer commerce.
 */
object DtcBillingEventModel : riven.core.models.core.CoreModelDefinition(
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
