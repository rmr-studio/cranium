package riven.core.models.core.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityTypeRole
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute
import riven.core.models.core.ProjectionAcceptRule

/**
 * Acquisition Source — a marketing channel or campaign that brings users to the product.
 */
object AcquisitionSourceModel : riven.core.models.core.CoreModelDefinition(
    key = "acquisition-source",
    displayNameSingular = "Acquisition Source",
    displayNamePlural = "Acquisition Sources",
    iconType = IconType.MEGAPHONE,
    iconColour = IconColour.PURPLE,
    semanticGroup = SemanticGroup.OPERATIONAL,
    lifecycleDomain = LifecycleDomain.ACQUISITION,
    role = EntityTypeRole.CATALOG,
    identifierKey = "name",
    semanticDefinition = "A marketing channel or campaign that brings users to the product. Used for attribution and channel quality analysis.",
    semanticTags = listOf("marketing", "acquisition", "attribution"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.ACQUISITION,
            semanticGroup = SemanticGroup.OPERATIONAL,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Name", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Name of the acquisition channel or campaign.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "marketing"),
            ),
        ),
        "type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Type", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf(
                    "paid-search",
                    "paid-social",
                    "organic",
                    "email",
                    "referral",
                    "affiliate",
                    "direct",
                    "product-hunt",
                    "content"
                )
            ),
            semantics = AttributeSemantics(
                definition = "Category of acquisition channel.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("marketing", "channel-type"),
            ),
        ),
        "spend" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Spend", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Total spend on this channel or campaign.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("marketing-spend", "budget"),
            ),
        ),
        "active" to CoreModelAttribute(
            schemaType = SchemaType.CHECKBOX, label = "Active", dataType = DataType.BOOLEAN,
            semantics = AttributeSemantics(
                definition = "Whether this channel or campaign is currently running.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("status"),
            ),
        ),
    ),
)
