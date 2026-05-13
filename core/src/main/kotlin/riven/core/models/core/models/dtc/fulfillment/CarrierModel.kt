package cranium.core.models.core.models.dtc.fulfillment

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.core.AttributeSemantics
import cranium.core.models.core.CoreModelAttribute
import cranium.core.models.core.CoreModelDefinition
import cranium.core.models.core.ProjectionAcceptRule

/**
 * Carrier — a shipping carrier (USPS, UPS, FedEx, DHL, etc.) used by one or more shipments.
 */
object CarrierModel : CoreModelDefinition(
    key = "carrier",
    displayNameSingular = "Carrier",
    displayNamePlural = "Carriers",
    iconType = IconType.SHIP,
    iconColour = IconColour.TEAL,
    semanticGroup = SemanticGroup.OPERATIONAL,
    role = EntityTypeRole.CATALOG,
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
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("tracking", "url"),
            ),
        ),
    ),
)
