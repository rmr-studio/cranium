package riven.core.models.core.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule

/**
 * Product — a sellable item in the store catalogue. DTC E-commerce specific.
 */
object ProductModel : riven.core.models.core.CoreModelDefinition(
    key = "product",
    displayNameSingular = "Product",
    displayNamePlural = "Products",
    iconType = IconType.PACKAGE,
    iconColour = IconColour.YELLOW,
    semanticGroup = SemanticGroup.PRODUCT,
    lifecycleDomain = LifecycleDomain.USAGE,
    identifierKey = "name",
    semanticDefinition = "A sellable item in the store catalogue. Products are what customers purchase via orders.",
    semanticTags = listOf("catalogue", "inventory", "ecommerce"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.USAGE,
            semanticGroup = SemanticGroup.PRODUCT,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Name", dataType = DataType.STRING,
            required = true,
            semantics = AttributeSemantics(
                definition = "Product name as displayed to customers.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "catalogue"),
            ),
        ),
        "sku" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "SKU", dataType = DataType.STRING,
            unique = true,
            semantics = AttributeSemantics(
                definition = "Stock keeping unit for inventory tracking.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("inventory", "unique-key"),
            ),
        ),
        "price" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Price", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Retail selling price.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("pricing", "revenue"),
            ),
        ),
        "category" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Category", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf(
                    "apparel",
                    "electronics",
                    "home",
                    "beauty",
                    "food",
                    "accessories",
                    "other"
                )
            ),
            semantics = AttributeSemantics(
                definition = "Product category for organisation and reporting.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("catalogue", "classification"),
            ),
        ),
    ),
)
