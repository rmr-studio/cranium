package cranium.core.models.core.models

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.models.common.validation.SchemaOptions
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.ProjectionAcceptRule

/**
 * Product — a sellable item in the store catalogue. DTC E-commerce specific.
 */
object ProductModel : cranium.core.models.core.CoreModelDefinition(
    key = "product",
    displayNameSingular = "Product",
    displayNamePlural = "Products",
    role = EntityTypeRole.CATALOG,
    iconType = IconType.PACKAGE,
    iconColour = IconColour.YELLOW,
    semanticGroup = SemanticGroup.PRODUCT,
    lifecycleDomain = LifecycleDomain.COMMERCE,
    identifierKey = "name",
    semanticDefinition = "A sellable item in the store catalogue. Products are what customers purchase via orders.",
    semanticTags = listOf("catalogue", "inventory", "ecommerce"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.COMMERCE,
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
