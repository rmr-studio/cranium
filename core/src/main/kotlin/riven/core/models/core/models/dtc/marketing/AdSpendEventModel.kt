package cranium.core.models.core.models.dtc.marketing

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.ProjectionAcceptRule

/**
 * Ad Spend Event — a daily spend record for a specific creative (and optionally audience).
 * Granularity is typically one row per (creative, audience, date).
 */
object AdSpendEventModel : cranium.core.models.core.CoreModelDefinition(
    key = "ad-spend-event",
    displayNameSingular = "Ad Spend Event",
    displayNamePlural = "Ad Spend Events",
    iconType = IconType.COINS,
    iconColour = IconColour.YELLOW,
    semanticGroup = SemanticGroup.FINANCIAL,
    lifecycleDomain = LifecycleDomain.MARKETING,
    role = EntityTypeRole.CATALOG,
    identifierKey = "external-id",
    semanticDefinition = "A daily spend event for a specific ad creative. Captures spend, impressions, clicks, and conversions at the lowest meaningful attribution grain.",
    semanticTags = listOf("marketing", "spend", "performance", "daily"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.MARKETING,
            semanticGroup = SemanticGroup.FINANCIAL,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "external-id" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "External ID", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Platform-native ad spend event identifier.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "external"),
            ),
        ),
        "date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Date", dataType = DataType.STRING,
            required = true, format = "date",
            semantics = AttributeSemantics(
                definition = "Calendar date of the spend.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "daily"),
            ),
        ),
        "spend" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Spend", dataType = DataType.NUMBER,
            required = true, format = "currency",
            semantics = AttributeSemantics(
                definition = "Money spent on this creative on this date.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("spend", "budget"),
            ),
        ),
        "impressions" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Impressions", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Number of times the creative was shown.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("reach", "performance"),
            ),
        ),
        "clicks" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Clicks", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Number of clicks on the creative.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("engagement", "performance"),
            ),
        ),
        "conversions" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Conversions", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Number of conversions attributed to the creative on this date.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("conversion", "performance"),
            ),
        ),
        "audience" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Audience", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Audience/target label for this spend row.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("audience", "targeting"),
            ),
        ),
    ),
)
