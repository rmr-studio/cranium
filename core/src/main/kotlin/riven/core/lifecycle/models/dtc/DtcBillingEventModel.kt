package riven.core.lifecycle.models.dtc

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.models.common.validation.SchemaOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition
import riven.core.lifecycle.ProjectionAcceptRule
import riven.core.lifecycle.models.base.BillingEventBase

/**
 * DTC Billing Event — a financial event in the ecommerce transaction lifecycle.
 * Includes purchase and shipping-fee event types specific to direct-to-consumer commerce.
 */
object DtcBillingEventModel : CoreModelDefinition(
    key = "billing-event",
    displayNameSingular = "Billing Event",
    displayNamePlural = "Billing Events",
    iconType = IconType.CREDIT_CARD,
    iconColour = IconColour.GREEN,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.BILLING,
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
