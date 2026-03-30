package riven.core.lifecycle.models

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
import riven.core.lifecycle.ProjectionAcceptRule

/**
 * Feature Usage Event — records product feature usage by a customer. B2C SaaS specific.
 */
object FeatureUsageEventModel : CoreModelDefinition(
    key = "feature-usage-event",
    displayNameSingular = "Feature Usage Event",
    displayNamePlural = "Feature Usage Events",
    iconType = IconType.ACTIVITY,
    iconColour = IconColour.TEAL,
    semanticGroup = SemanticGroup.OPERATIONAL,
    lifecycleDomain = LifecycleDomain.USAGE,
    identifierKey = "feature-name",
    semanticDefinition = "Records product feature usage by a customer. Used for activation analysis, feature adoption tracking, and engagement scoring.",
    semanticTags = listOf("product-analytics", "engagement", "activation"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.USAGE,
            semanticGroup = SemanticGroup.OPERATIONAL,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "feature-name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Feature", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Name of the feature that was used.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "feature"),
            ),
        ),
        "action" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Action", dataType = DataType.STRING,
            options = AttributeOptions(enum = listOf("viewed", "used", "completed", "error")),
            semantics = AttributeSemantics(
                definition = "What the user did with the feature.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("event-type", "engagement"),
            ),
        ),
        "date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Date", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "When the usage event occurred.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "activity"),
            ),
        ),
        "count" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Count", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Number of times this action was performed in this event.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("engagement", "frequency"),
            ),
        ),
    ),
)
