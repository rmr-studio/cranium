package riven.core.lifecycle.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.models.common.validation.DefaultValue
import riven.core.models.common.validation.SchemaOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition
import riven.core.lifecycle.ProjectionAcceptRule

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
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.BILLING,
            semanticGroup = SemanticGroup.TRANSACTION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "plan-name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Plan", dataType = DataType.STRING,
            required = true,
            semantics = AttributeSemantics(
                definition = "Name of the subscription plan.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "plan"),
            ),
        ),
        "status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Status", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("trialing", "active", "past-due", "cancelled", "paused"), defaultValue = DefaultValue.Static("trialing")),
            semantics = AttributeSemantics(
                definition = "Current subscription status.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "billing"),
            ),
        ),
        "mrr" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "MRR", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Monthly recurring revenue from this subscription.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("revenue", "metrics"),
            ),
        ),
        "billing-interval" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Billing Interval", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("monthly", "quarterly", "annual")),
            semantics = AttributeSemantics(
                definition = "How frequently the subscription is billed.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("billing", "plan"),
            ),
        ),
        "start-date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Start Date", dataType = DataType.STRING,
            format = "date",
            options = SchemaOptions(defaultValue = DefaultValue.Dynamic(DynamicDefaultFunction.CURRENT_DATE)),
            semantics = AttributeSemantics(
                definition = "Date the subscription started.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "onboarding"),
            ),
        ),
        "cancel-date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Cancel Date", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the subscription was cancelled, if applicable.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "churn"),
            ),
        ),
    ),
)
