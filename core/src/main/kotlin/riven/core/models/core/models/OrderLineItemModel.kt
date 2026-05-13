package cranium.core.models.core.models

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.ProjectionAcceptRule

/**
 * Order Line Item — an individual product entry within an order.
 * Captures quantity, unit price, and discount per product, replacing
 * the direct order-to-product MANY_TO_MANY relationship.
 */
object OrderLineItemModel : cranium.core.models.core.CoreModelDefinition(
    key = "order-line-item",
    displayNameSingular = "Line Item",
    displayNamePlural = "Line Items",
    iconType = IconType.LIST,
    iconColour = IconColour.GREEN,
    role = EntityTypeRole.CATALOG,
    semanticGroup = SemanticGroup.LINE_ITEM,
    lifecycleDomain = LifecycleDomain.BILLING,
    identifierKey = "quantity",
    semanticDefinition = "An individual product entry within an order, capturing quantity, pricing, and discount for a specific product.",
    semanticTags = listOf("line-item", "transaction", "ecommerce"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.BILLING,
            semanticGroup = SemanticGroup.LINE_ITEM,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "quantity" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Quantity", dataType = DataType.NUMBER,
            required = true,
            semantics = AttributeSemantics(
                definition = "Number of units of the product in this line item.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("quantity", "order"),
            ),
        ),
        "unit-price" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Unit Price", dataType = DataType.NUMBER,
            required = true, format = "currency",
            semantics = AttributeSemantics(
                definition = "Price per unit at the time of purchase.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("pricing", "revenue"),
            ),
        ),
        "discount" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Discount", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Discount amount applied to this line item.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("pricing", "discount"),
            ),
        ),
        "variant-id" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Variant ID", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Identifier for the specific product variant (size, colour, etc.).",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("variant", "catalogue"),
            ),
        ),
    ),
)
