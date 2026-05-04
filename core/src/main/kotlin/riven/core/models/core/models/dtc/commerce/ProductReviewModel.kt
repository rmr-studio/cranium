package riven.core.models.core.models.dtc.commerce

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
 * Product Review — a customer-submitted review of a product. Source of sentiment
 * and quality signal at the product level.
 */
object ProductReviewModel : riven.core.models.core.CoreModelDefinition(
    key = "product-review",
    displayNameSingular = "Product Review",
    displayNamePlural = "Product Reviews",
    iconType = IconType.STAR,
    iconColour = IconColour.YELLOW,
    semanticGroup = SemanticGroup.REVIEW,
    lifecycleDomain = LifecycleDomain.ENGAGEMENT,
    role = EntityTypeRole.CATALOG,
    identifierKey = "external-id",
    semanticDefinition = "A customer review of a product. Captures rating, body, and verified-buyer status for downstream sentiment and quality analysis.",
    semanticTags = listOf("review", "rating", "sentiment"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.ENGAGEMENT,
            semanticGroup = SemanticGroup.REVIEW,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "external-id" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "External ID", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "External review identifier from the review platform.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "external"),
            ),
        ),
        "rating" to CoreModelAttribute(
            schemaType = SchemaType.RATING, label = "Rating", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Customer rating (typically 1–5).",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("rating"),
            ),
        ),
        "title" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Title", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Review headline.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("headline"),
            ),
        ),
        "body" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Body", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Review body text.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("content", "text"),
            ),
        ),
        "verified-buyer" to CoreModelAttribute(
            schemaType = SchemaType.CHECKBOX, label = "Verified Buyer", dataType = DataType.BOOLEAN,
            semantics = AttributeSemantics(
                definition = "Whether the reviewer is a verified buyer of the product.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("verification", "trust"),
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
                definition = "Timestamp the review was posted.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
    ),
)
