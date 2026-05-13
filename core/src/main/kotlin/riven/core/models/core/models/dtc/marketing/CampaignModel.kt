package cranium.core.models.core.models.dtc.marketing

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.common.validation.SchemaOptions
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.ProjectionAcceptRule

/**
 * Campaign — a paid marketing campaign running on an ad platform (Meta, Google, TikTok, etc.).
 */
object CampaignModel : cranium.core.models.core.CoreModelDefinition(
    key = "campaign",
    displayNameSingular = "Campaign",
    displayNamePlural = "Campaigns",
    iconType = IconType.MEGAPHONE,
    iconColour = IconColour.PURPLE,
    semanticGroup = SemanticGroup.CAMPAIGN,
    lifecycleDomain = LifecycleDomain.MARKETING,
    role = EntityTypeRole.CATALOG,
    identifierKey = "name",
    semanticDefinition = "A paid marketing campaign running on an ad platform. Groups ad creatives and drives spend against a targeted audience.",
    semanticTags = listOf("marketing", "advertising", "campaign"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.MARKETING,
            semanticGroup = SemanticGroup.CAMPAIGN,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Name", dataType = DataType.STRING,
            required = true,
            semantics = AttributeSemantics(
                definition = "Campaign name as defined in the ad platform.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "campaign"),
            ),
        ),
        "platform" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Platform", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("meta", "google", "tiktok", "linkedin", "twitter", "pinterest", "other")
            ),
            semantics = AttributeSemantics(
                definition = "Ad platform running the campaign.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("platform", "channel"),
            ),
        ),
        "objective" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Objective", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("awareness", "traffic", "engagement", "leads", "conversions", "sales", "retention")
            ),
            semantics = AttributeSemantics(
                definition = "Primary campaign objective.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("objective"),
            ),
        ),
        "status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Status", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("draft", "active", "paused", "completed", "archived")),
            semantics = AttributeSemantics(
                definition = "Current run state of the campaign.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "status"),
            ),
        ),
        "start-date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Start Date", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the campaign began running.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "end-date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "End Date", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the campaign ended or is scheduled to end.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "budget" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Budget", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Total budget allocated to the campaign.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("spend", "budget"),
            ),
        ),
    ),
)
