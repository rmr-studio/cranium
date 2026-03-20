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
 * Subscription — a recurring subscription plan held by a customer. B2C SaaS specific.
 */
object SubscriptionModel : CoreModelDefinition(
    key = "subscription",
    displayNameSingular = "Subscription",
    displayNamePlural = "Subscriptions",
    iconType = IconType.REPEAT,
    iconColour = IconColour.GREEN,
    semanticGroup = SemanticGroup.TRANSACTION,
    lifecycleDomain = LifecycleDomain.BILLING,
    identifierKey = "plan-name",
    semanticDefinition = "A recurring subscription plan held by a customer. The core revenue relationship in a SaaS business.",
    semanticTags = listOf("subscription", "recurring-revenue", "saas", "billing"),
    attributes = mapOf(
        "plan-name" to CoreModelAttribute(
            key = "plan-name", schemaType = "TEXT", label = "Plan", dataType = "string",
            semantics = AttributeSemantics(
                definition = "Name of the subscription plan.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "plan"),
            ),
        ),
        "status" to CoreModelAttribute(
            key = "status", schemaType = "SELECT", label = "Status", dataType = "string",
            options = AttributeOptions(enum = listOf("trialing", "active", "past-due", "cancelled", "paused"), default = "trialing"),
            semantics = AttributeSemantics(
                definition = "Current subscription status.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "billing"),
            ),
        ),
        "mrr" to CoreModelAttribute(
            key = "mrr", schemaType = "CURRENCY", label = "MRR", dataType = "number",
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Monthly recurring revenue from this subscription.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("revenue", "metrics"),
            ),
        ),
        "billing-interval" to CoreModelAttribute(
            key = "billing-interval", schemaType = "SELECT", label = "Billing Interval", dataType = "string",
            options = AttributeOptions(enum = listOf("monthly", "quarterly", "annual")),
            semantics = AttributeSemantics(
                definition = "How frequently the subscription is billed.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("billing", "plan"),
            ),
        ),
        "start-date" to CoreModelAttribute(
            key = "start-date", schemaType = "DATE", label = "Start Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the subscription started.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "onboarding"),
            ),
        ),
        "cancel-date" to CoreModelAttribute(
            key = "cancel-date", schemaType = "DATE", label = "Cancel Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the subscription was cancelled, if applicable.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "churn"),
            ),
        ),
    ),
)
