package riven.core.models.core.models.dtc.social

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule

/**
 * Social Comment — a comment left on one of the brand's owned social posts.
 */
object SocialCommentModel : riven.core.models.core.CoreModelDefinition(
    key = "social-comment",
    displayNameSingular = "Social Comment",
    displayNamePlural = "Social Comments",
    iconType = IconType.MESSAGE_CIRCLE,
    iconColour = IconColour.PINK,
    semanticGroup = SemanticGroup.COMMUNICATION,
    lifecycleDomain = LifecycleDomain.ENGAGEMENT,
    identifierKey = "external-id",
    semanticDefinition = "A comment left by a user on one of the brand's owned social posts. Source for sentiment, support-adjacent signals, and VIP identification.",
    semanticTags = listOf("social", "engagement", "communication"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.ENGAGEMENT,
            semanticGroup = SemanticGroup.COMMUNICATION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "external-id" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "External ID", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Platform-native comment identifier.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "external"),
            ),
        ),
        "author-handle" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Author Handle", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Platform handle of the comment author.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("author", "social-handle"),
            ),
        ),
        "body" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Body", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Comment text.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("content", "text"),
            ),
        ),
        "posted-at" to CoreModelAttribute(
            schemaType = SchemaType.DATETIME, label = "Posted At", dataType = DataType.STRING,
            format = "date-time",
            semantics = AttributeSemantics(
                definition = "Timestamp the comment was posted.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "likes" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Likes", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Likes or reactions on the comment.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("engagement"),
            ),
        ),
    ),
)
