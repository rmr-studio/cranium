package riven.core.models.core.models.dtc.fulfillment

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule

/**
 * Shipment — a package in transit from the warehouse to a customer. DTC fulfillment specific.
 */
object ShipmentModel : riven.core.models.core.CoreModelDefinition(
    key = "shipment",
    displayNameSingular = "Shipment",
    displayNamePlural = "Shipments",
    iconType = IconType.PACKAGE,
    iconColour = IconColour.BLUE,
    semanticGroup = SemanticGroup.SHIPMENT,
    lifecycleDomain = LifecycleDomain.FULFILLMENT,
    identifierKey = "tracking-number",
    semanticDefinition = "A package in transit from the warehouse to the customer. Tracks lane, service level, and status through delivery.",
    semanticTags = listOf("fulfillment", "shipping", "logistics"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.FULFILLMENT,
            semanticGroup = SemanticGroup.SHIPMENT,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "tracking-number" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Tracking Number", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Carrier-issued tracking number.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "tracking"),
            ),
        ),
        "service-level" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Service Level", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("standard", "expedited", "overnight", "two-day", "ground", "economy", "international")
            ),
            semantics = AttributeSemantics(
                definition = "Shipping service level.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("service", "sla"),
            ),
        ),
        "lane" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Lane", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Carrier lane identifier (origin-destination zone).",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("logistics", "routing"),
            ),
        ),
        "status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Status", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf(
                    "label_created",
                    "picked_up",
                    "in_transit",
                    "out_for_delivery",
                    "delivered",
                    "exception",
                    "returned"
                )
            ),
            semantics = AttributeSemantics(
                definition = "Current lifecycle state of the shipment.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "status"),
            ),
        ),
        "shipped-at" to CoreModelAttribute(
            schemaType = SchemaType.DATETIME, label = "Shipped At", dataType = DataType.STRING,
            format = "date-time",
            semantics = AttributeSemantics(
                definition = "Timestamp when the shipment was picked up or shipped.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "delivered-at" to CoreModelAttribute(
            schemaType = SchemaType.DATETIME, label = "Delivered At", dataType = DataType.STRING,
            format = "date-time",
            semantics = AttributeSemantics(
                definition = "Timestamp when the shipment was delivered.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline", "delivery"),
            ),
        ),
        "cost" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Cost", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Shipping cost paid for this shipment.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("cost", "shipping"),
            ),
        ),
    ),
)
