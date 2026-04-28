package riven.core.models.core.models.dtc.commerce

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
 * Collection — a merchandising grouping of products (Shopify collection, category).
 */
object CollectionModel : riven.core.models.core.CoreModelDefinition(
    key = "collection",
    displayNameSingular = "Collection",
    displayNamePlural = "Collections",
    iconType = IconType.FOLDER,
    iconColour = IconColour.ORANGE,
    semanticGroup = SemanticGroup.COLLECTION,
    lifecycleDomain = LifecycleDomain.COMMERCE,
    identifierKey = "handle",
    semanticDefinition = "A merchandising grouping of products — mirrors Shopify collections (manual or rules-based).",
    semanticTags = listOf("catalogue", "merchandising", "collection"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.COMMERCE,
            semanticGroup = SemanticGroup.COLLECTION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "handle" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Handle", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "URL-safe handle of the collection.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "slug"),
            ),
        ),
        "title" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Title", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Display title of the collection.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name"),
            ),
        ),
        "description" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Description", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Collection description shown on storefront.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("description"),
            ),
        ),
        "product-count" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Product Count", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Number of products currently in the collection.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("catalogue"),
            ),
        ),
    ),
)
