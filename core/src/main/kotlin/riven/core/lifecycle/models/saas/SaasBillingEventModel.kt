package riven.core.lifecycle.models.saas

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
 * SaaS Billing Event — a financial event in the SaaS subscription lifecycle.
 * Includes trial-start and trial-end event types specific to subscription billing.
 */
object SaasBillingEventModel : CoreModelDefinition(
    key = "billing-event",
    displayNameSingular = "Billing Event",
    displayNamePlural = "Billing Events",
    iconType = IconType.CREDIT_CARD,
    iconColour = IconColour.GREEN,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.BILLING,
    identifierKey = "description",
    semanticDefinition = "A financial event in the SaaS subscription lifecycle — charges, refunds, credits, trial events, or adjustments.",
    semanticTags = listOf("billing", "finance", "revenue", "subscription"),
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
            options = SchemaOptions(enum = listOf("charge", "refund", "credit", "adjustment", "payout", "trial-start", "trial-end")),
            semantics = AttributeSemantics(
                definition = "The type of billing event, including subscription-specific trial events.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("billing", "classification"),
            ),
        ),
    ),
)
