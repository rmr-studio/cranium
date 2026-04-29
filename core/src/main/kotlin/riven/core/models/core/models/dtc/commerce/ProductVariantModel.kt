package riven.core.models.core.models.dtc.commerce

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityTypeRole
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule

/**
 * Product Variant — a SKU-level variant of a Product (size, colour, flavour, etc.).
 */
object ProductVariantModel : riven.core.models.core.CoreModelDefinition(
    key = "product-variant",
    displayNameSingular = "Product Variant",
    displayNamePlural = "Product Variants",
    iconType = IconType.LAYERS,
    iconColour = IconColour.YELLOW,
    semanticGroup = SemanticGroup.PRODUCT_VARIANT,
    lifecycleDomain = LifecycleDomain.COMMERCE,
    role = EntityTypeRole.CATALOG,
    identifierKey = "sku",
    semanticDefinition = "A SKU-level variant of a Product capturing size, colour, or other option dimensions.",
    semanticTags = listOf("catalogue", "inventory", "sku"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.COMMERCE,
            semanticGroup = SemanticGroup.PRODUCT_VARIANT,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "sku" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "SKU", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Stock keeping unit — unique variant identifier.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "inventory"),
            ),
        ),
        "title" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Title", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Variant display title (e.g. \"Medium / Black\").",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name"),
            ),
        ),
        "option-1" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Option 1", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "First option value (e.g. size).",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("option", "variant"),
            ),
        ),
        "option-2" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Option 2", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Second option value (e.g. colour).",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("option", "variant"),
            ),
        ),
        "option-3" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Option 3", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Third option value.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("option", "variant"),
            ),
        ),
        "price" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Price", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Variant-specific selling price.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("pricing"),
            ),
        ),
        "inventory" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Inventory", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Current on-hand inventory for this variant.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("inventory", "stock"),
            ),
        ),
    ),
)
