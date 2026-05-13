package cranium.core.models.core.models.dtc.commerce

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
 * Return — a product return with a classified reason. Source for return-rate and
 * return-cohort analysis. Reason is classified via a taxonomy (set at ingestion, optionally LLM-backed).
 */
object ReturnModel : cranium.core.models.core.CoreModelDefinition(
    key = "return",
    displayNameSingular = "Return",
    displayNamePlural = "Returns",
    iconType = IconType.UNDO,
    iconColour = IconColour.RED,
    semanticGroup = SemanticGroup.TRANSACTION,
    lifecycleDomain = LifecycleDomain.RETENTION,
    role = EntityTypeRole.CATALOG,
    identifierKey = "rma-number",
    semanticDefinition = "A product return. Captures the classified return reason, refund amount, and link to the originating order.",
    semanticTags = listOf("return", "refund", "retention"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.RETENTION,
            semanticGroup = SemanticGroup.TRANSACTION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
        "rma-number" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "RMA Number", dataType = DataType.STRING,
            required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "Return merchandise authorisation number.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("unique-key", "reference"),
            ),
        ),
        "reason" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Reason", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf(
                    "damage",
                    "wrong_size",
                    "quality",
                    "expectation_mismatch",
                    "shipping_delay",
                    "changed_mind",
                    "other"
                )
            ),
            semantics = AttributeSemantics(
                definition = "Classified return reason from the fixed taxonomy.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("reason", "classification"),
            ),
        ),
        "reason-text" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Reason Text", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Raw customer-provided reason text (pre-classification).",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("freetext", "source"),
            ),
        ),
        "refund-amount" to CoreModelAttribute(
            schemaType = SchemaType.CURRENCY, label = "Refund Amount", dataType = DataType.NUMBER,
            format = "currency",
            semantics = AttributeSemantics(
                definition = "Amount refunded to the customer.",
                classification = SemanticAttributeClassification.QUANTITATIVE,
                tags = listOf("refund", "financial"),
            ),
        ),
        "status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Status", dataType = DataType.STRING,
            options = SchemaOptions(
                enum = listOf("requested", "approved", "received", "refunded", "rejected", "closed")
            ),
            semantics = AttributeSemantics(
                definition = "Current lifecycle state of the return.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "status"),
            ),
        ),
        "requested-at" to CoreModelAttribute(
            schemaType = SchemaType.DATETIME, label = "Requested At", dataType = DataType.STRING,
            format = "date-time",
            semantics = AttributeSemantics(
                definition = "Timestamp the return was requested.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
    ),
)
