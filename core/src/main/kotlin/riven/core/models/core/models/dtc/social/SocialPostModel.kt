package riven.core.models.core.models.dtc.social

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityTypeRole
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule

/**
 * Social Post — an owned-account social post (brand's own Instagram, TikTok, etc.).
 */
object SocialPostModel : riven.core.models.core.CoreModelDefinition(
    key = "social-post",
    displayNameSingular = "Social Post",
    displayNamePlural = "Social Posts",
    iconType = IconType.SHARE,
    iconColour = IconColour.PINK,
    semanticGroup = SemanticGroup.SOCIAL_POST,
    lifecycleDomain = LifecycleDomain.ENGAGEMENT,
    role = EntityTypeRole.CATALOG,
    identifierKey = "permalink",
    semanticDefinition = "An owned-account social post published by the brand. Source for engagement metrics and organic reach.",
    semanticTags = listOf("social", "content", "engagement"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.ENGAGEMENT,
            semanticGroup = SemanticGroup.SOCIAL_POST,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "permalink" to CoreModelAttribute(
            schemaType = SchemaType.URL, label = "Permalink", dataType = DataType.STRING,
            required = true, unique = true, format = "uri",
            semantics = AttributeSemantics(
                definition = "Canonical URL of the post.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "link"),
            ),
        ),
        "platform" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Platform", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("instagram", "tiktok", "facebook", "twitter", "youtube", "linkedin", "pinterest", "other")
            ),
            semantics = AttributeSemantics(
                definition = "Social platform the post was published on.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("platform", "channel"),
            ),
        ),
        "format" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Format", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("image", "video", "carousel", "story", "reel", "text", "live")),
            semantics = AttributeSemantics(
                definition = "Content format of the post.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("format"),
            ),
        ),
        "caption" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Caption", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Caption or body text on the post.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("content", "copy"),
            ),
        ),
        "published-at" to CoreModelAttribute(
            schemaType = SchemaType.DATETIME, label = "Published At", dataType = DataType.STRING,
            format = "date-time",
            semantics = AttributeSemantics(
                definition = "Timestamp the post was published.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "likes" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Likes", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Total likes or reactions on the post.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("engagement"),
            ),
        ),
        "comments" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Comments", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Total comment count on the post.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("engagement"),
            ),
        ),
    ),
)
