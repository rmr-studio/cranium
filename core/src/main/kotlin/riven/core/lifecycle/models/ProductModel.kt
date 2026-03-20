package riven.core.lifecycle.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.lifecycle.AttributeOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition

/**
 * Product — a sellable item in the store catalogue. DTC E-commerce specific.
 */
object ProductModel : CoreModelDefinition(
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
    attributes = mapOf(
        "name" to CoreModelAttribute(
            key = "name", schemaType = "TEXT", label = "Name", dataType = "string",
            semantics = AttributeSemantics(
                definition = "Product name as displayed to customers.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "catalogue"),
            ),
        ),
        "sku" to CoreModelAttribute(
            key = "sku", schemaType = "TEXT", label = "SKU", dataType = "string",
            unique = true,
            semantics = AttributeSemantics(
                definition = "Stock keeping unit for inventory tracking.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("inventory", "unique-key"),
            ),
        ),
        "price" to CoreModelAttribute(
            key = "price", schemaType = "CURRENCY", label = "Price", dataType = "number",
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Retail selling price.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("pricing", "revenue"),
            ),
        ),
        "category" to CoreModelAttribute(
            key = "category", schemaType = "SELECT", label = "Category", dataType = "string",
            options = AttributeOptions(enum = listOf("apparel", "electronics", "home", "beauty", "food", "accessories", "other")),
            semantics = AttributeSemantics(
                definition = "Product category for organisation and reporting.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("catalogue", "classification"),
            ),
        ),
    ),
)
