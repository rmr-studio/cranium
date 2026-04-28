package riven.core.models.core.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.models.common.validation.DefaultValue
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule

/**
 * Order — a customer purchase order. DTC E-commerce specific.
 */
object OrderModel : riven.core.models.core.CoreModelDefinition(
    key = "order",
    displayNameSingular = "Order",
    displayNamePlural = "Orders",
    iconType = IconType.SHOPPING_CART,
    iconColour = IconColour.GREEN,
    semanticGroup = SemanticGroup.TRANSACTION,
    lifecycleDomain = LifecycleDomain.BILLING,
    identifierKey = "order-number",
    semanticDefinition = "A customer purchase order. The core revenue event in the e-commerce lifecycle, linking customers to products and payments.",
    semanticTags = listOf("purchase", "transaction", "ecommerce", "revenue"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.BILLING,
            semanticGroup = SemanticGroup.TRANSACTION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "order-number" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Order Number", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Unique order identifier from the e-commerce platform.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("reference", "unique-key"),
            ),
        ),
        "total" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Total", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Total order amount including tax and shipping.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("revenue", "billing"),
            ),
        ),
        "status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Status", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf(
                    "pending",
                    "confirmed",
                    "shipped",
                    "delivered",
                    "cancelled",
                    "returned"
                ), defaultValue = DefaultValue.Static("pending")
            ),
            semantics = AttributeSemantics(
                definition = "Current fulfilment status of the order.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "fulfilment"),
            ),
        ),
        "order-date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Order Date", dataType = DataType.STRING,
            format = "date",
            options = SchemaOptions(defaultValue = DefaultValue.Dynamic(DynamicDefaultFunction.CURRENT_DATE)),
            semantics = AttributeSemantics(
                definition = "Date the order was placed.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "purchase"),
            ),
        ),
        "payment-status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Payment Status", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("pending", "paid", "refunded", "failed"),
                defaultValue = DefaultValue.Static("pending")
            ),
            semantics = AttributeSemantics(
                definition = "Current payment state for this order.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("payment", "billing"),
            ),
        ),
    ),
)
