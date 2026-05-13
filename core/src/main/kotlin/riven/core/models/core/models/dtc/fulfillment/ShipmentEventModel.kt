package cranium.core.models.core.models.dtc.fulfillment

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.common.validation.SchemaOptions
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.ProjectionAcceptRule

/**
 * Shipment Event — a status update for a shipment (label_created, in_transit, delivered, exception).
 */
object ShipmentEventModel : cranium.core.models.core.CoreModelDefinition(
    key = "shipment-event",
    displayNameSingular = "Shipment Event",
    displayNamePlural = "Shipment Events",
    iconType = IconType.TRUCK,
    iconColour = IconColour.BLUE,
    semanticGroup = SemanticGroup.SHIPMENT_EVENT,
    role = EntityTypeRole.CATALOG,
    lifecycleDomain = LifecycleDomain.FULFILLMENT,
    identifierKey = "external-id",
    semanticDefinition = "A status update on a shipment — captures the event stream from carrier scans.",
    semanticTags = listOf("fulfillment", "tracking", "event"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.FULFILLMENT,
            semanticGroup = SemanticGroup.SHIPMENT_EVENT,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "external-id" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "External ID", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Platform-native shipment event identifier.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "external"),
            ),
        ),
        "event-type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Event Type", dataType = DataType.STRING,
            required = true,
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
                definition = "The type of shipment event recorded.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("event-type", "status"),
            ),
        ),
        "occurred-at" to CoreModelAttribute(
            schemaType = SchemaType.DATETIME, label = "Occurred At", dataType = DataType.STRING,
            required = true, format = "date-time",
            semantics = AttributeSemantics(
                definition = "Timestamp the event was recorded by the carrier.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "location" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Location", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Location reported by the carrier at the time of the event.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("location", "tracking"),
            ),
        ),
        "description" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Description", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Human-readable description of the event.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("description", "notes"),
            ),
        ),
    ),
)
