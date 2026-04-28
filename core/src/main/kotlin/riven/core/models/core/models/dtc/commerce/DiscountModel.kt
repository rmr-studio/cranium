package riven.core.models.core.models.dtc.commerce

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
 * Discount — a promotional discount code or automatic discount applied to orders.
 */
object DiscountModel : riven.core.models.core.CoreModelDefinition(
    key = "discount",
    displayNameSingular = "Discount",
    displayNamePlural = "Discounts",
    iconType = IconType.PERCENT,
    iconColour = IconColour.ORANGE,
    semanticGroup = SemanticGroup.PROMOTION,
    lifecycleDomain = LifecycleDomain.MARKETING,
    identifierKey = "code",
    semanticDefinition = "A promotional discount code or automatic discount applied at checkout. Source for discount-dependency signals and promo performance.",
    semanticTags = listOf("promotion", "discount", "marketing"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.MARKETING,
            semanticGroup = SemanticGroup.PROMOTION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "code" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Code", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Discount code string entered at checkout (or a stable identifier for automatic discounts).",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "code"),
            ),
        ),
        "type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Type", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("percentage", "fixed_amount", "free_shipping", "bxgy")),
            semantics = AttributeSemantics(
                definition = "Discount mechanic.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("mechanic"),
            ),
        ),
        "value" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Value", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Discount value (percentage, fixed amount, etc. depending on type).",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("value"),
            ),
        ),
        "starts-at" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Starts At", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the discount becomes active.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "ends-at" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Ends At", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "Date the discount expires.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "usage-count" to CoreModelAttribute(
            schemaType = SchemaType.NUMBER, label = "Usage Count", dataType = DataType.NUMBER,
            semantics = AttributeSemantics(
                definition = "Number of times this discount has been redeemed.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("usage", "redemption"),
            ),
        ),
    ),
)
