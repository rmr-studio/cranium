package riven.core.lifecycle.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.lifecycle.AttributeOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition

/**
 * Billing Event — a financial event in the subscription lifecycle (charges, refunds, credits, trial events).
 */
object BillingEventModel : CoreModelDefinition(
    key = "billing-event",
    displayNameSingular = "Billing Event",
    displayNamePlural = "Billing Events",
    iconType = IconType.CREDIT_CARD,
    iconColour = IconColour.GREEN,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.BILLING,
    identifierKey = "description",
    semanticDefinition = "A financial event in the subscription lifecycle — charges, refunds, credits, trial events, or adjustments.",
    semanticTags = listOf("billing", "finance", "revenue", "subscription"),
    attributes = mapOf(
        "description" to CoreModelAttribute(
            key = "description", schemaType = "TEXT", label = "Description", dataType = "string",
            semantics = AttributeSemantics(
                definition = "Description of the billing event.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name"),
            ),
        ),
        "type" to CoreModelAttribute(
            key = "type", schemaType = "SELECT", label = "Type", dataType = "string",
            options = AttributeOptions(enum = listOf("charge", "refund", "credit", "adjustment", "payout", "trial-start", "trial-end")),
            semantics = AttributeSemantics(
                definition = "The type of billing event.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("billing", "classification"),
            ),
        ),
        "amount" to CoreModelAttribute(
            key = "amount", schemaType = "CURRENCY", label = "Amount", dataType = "number",
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Monetary value of this billing event.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("financial", "revenue"),
            ),
        ),
        "date" to CoreModelAttribute(
            key = "date", schemaType = "DATE", label = "Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the billing event occurred.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "billing"),
            ),
        ),
    ),
)
