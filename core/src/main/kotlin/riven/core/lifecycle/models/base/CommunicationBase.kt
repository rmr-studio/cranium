package riven.core.lifecycle.models.base

import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.models.common.validation.DefaultValue
import riven.core.models.common.validation.SchemaOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute

/**
 * Shared attributes for the Communication model across all business types.
 * The `type` enum and `follow-up-date` presence differ per business type.
 */
object CommunicationBase {

    val attributes = mapOf(
        "subject" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Subject", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "The subject or topic of the communication.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "topic"),
            ),
        ),
        "direction" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Direction", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("inbound", "outbound")),
            semantics = AttributeSemantics(
                definition = "Whether this communication was initiated by the client or by the team.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("direction", "initiative"),
            ),
        ),
        "date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Date", dataType = DataType.STRING,
            format = "date",
            options = SchemaOptions(defaultValue = DefaultValue.Dynamic(DynamicDefaultFunction.CURRENT_DATE)),
            semantics = AttributeSemantics(
                definition = "The date of the communication.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "summary" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Summary", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "A summary of what was discussed or communicated.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("notes", "context"),
            ),
        ),
        "outcome" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Outcome", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("positive", "neutral", "needs-action", "escalated")),
            semantics = AttributeSemantics(
                definition = "The outcome or disposition of this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("result", "disposition"),
            ),
        ),
        "channel" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Channel", dataType = DataType.STRING,
            options = SchemaOptions(enum = listOf("phone", "video", "in-person", "email", "chat")),
            semantics = AttributeSemantics(
                definition = "The specific channel used for this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("channel", "medium"),
            ),
        ),
    )
}
