package riven.core.models.core.models.dtc.fulfillment

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
 * Carrier — a shipping carrier (USPS, UPS, FedEx, DHL, etc.) used by one or more shipments.
 */
object CarrierModel : riven.core.models.core.CoreModelDefinition(
    key = "carrier",
    displayNameSingular = "Carrier",
    displayNamePlural = "Carriers",
    iconType = IconType.SHIP,
    iconColour = IconColour.TEAL,
    semanticGroup = SemanticGroup.OPERATIONAL,
    lifecycleDomain = LifecycleDomain.FULFILLMENT,
    identifierKey = "name",
    semanticDefinition = "A shipping carrier used by the brand. Carrier entities support carrier-level performance analysis (on-time %, exception rates).",
    semanticTags = listOf("fulfillment", "carrier", "logistics"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.FULFILLMENT,
            semanticGroup = SemanticGroup.OPERATIONAL,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Name", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Carrier name (e.g. USPS, UPS, FedEx).",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "unique-key"),
            ),
        ),
        "code" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Code", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Short carrier code (e.g. usps, ups, fedex, dhl).",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("code", "short-name"),
            ),
        ),
        "tracking-url-template" to CoreModelAttribute(
            schemaType = SchemaType.URL, label = "Tracking URL Template", dataType = DataType.STRING,
            format = "uri",
            semantics = AttributeSemantics(
                definition = "Template for building public tracking URLs (with a placeholder for the tracking number).",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("tracking", "url"),
            ),
        ),
    ),
)
