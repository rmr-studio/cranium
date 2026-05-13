package cranium.core.models.core.models.dtc.social

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
 * Social Mention — a brand mention from a non-owned account (UGC, customer content, press).
 */
object SocialMentionModel : cranium.core.models.core.CoreModelDefinition(
    key = "social-mention",
    displayNameSingular = "Social Mention",
    displayNamePlural = "Social Mentions",
    iconType = IconType.AT_SIGN,
    iconColour = IconColour.PINK,
    semanticGroup = SemanticGroup.SOCIAL_MENTION,
    lifecycleDomain = LifecycleDomain.ENGAGEMENT,
    role = EntityTypeRole.CATALOG,
    identifierKey = "permalink",
    semanticDefinition = "A brand mention on a non-owned social account (UGC, customer posts, press). Source for earned-reach tracking and unboxing/review signal.",
    semanticTags = listOf("social", "ugc", "mention", "earned-media"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.ENGAGEMENT,
            semanticGroup = SemanticGroup.SOCIAL_MENTION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "permalink" to CoreModelAttribute(
            schemaType = SchemaType.URL, label = "Permalink", dataType = DataType.STRING,
            required = true, unique = true, format = "uri",
            semantics = AttributeSemantics(
                definition = "Canonical URL of the mentioning post.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "link"),
            ),
        ),
        "platform" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Platform", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("instagram", "tiktok", "facebook", "twitter", "youtube", "reddit", "other")
            ),
            semantics = AttributeSemantics(
                definition = "Platform where the mention occurred.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("platform"),
            ),
        ),
        "author-handle" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Author Handle", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Handle of the non-owned author mentioning the brand.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("author", "social-handle"),
            ),
        ),
        "content" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Content", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Text content of the mention.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("content", "text"),
            ),
        ),
        "sentiment" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Sentiment", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("positive", "neutral", "negative", "mixed", "unknown")),
            semantics = AttributeSemantics(
                definition = "Derived sentiment classification.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("sentiment"),
            ),
        ),
        "posted-at" to CoreModelAttribute(
            schemaType = SchemaType.DATETIME, label = "Posted At", dataType = DataType.STRING,
            format = "date-time",
            semantics = AttributeSemantics(
                definition = "Timestamp the mention was posted.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "reach" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Reach", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Estimated reach or follower count of the author.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("reach", "earned-media"),
            ),
        ),
    ),
)
