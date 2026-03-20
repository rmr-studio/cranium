package riven.core.lifecycle.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.lifecycle.AttributeOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition

/**
 * Communication — a logged interaction with a customer, capturing context, outcome, and follow-ups.
 */
object CommunicationModel : CoreModelDefinition(
    key = "communication",
    displayNameSingular = "Communication",
    displayNamePlural = "Communications",
    iconType = IconType.MESSAGE_SQUARE,
    iconColour = IconColour.TEAL,
    semanticGroup = SemanticGroup.COMMUNICATION,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    identifierKey = "subject",
    semanticDefinition = "A communication logs a client interaction, capturing what was discussed, the outcome, and any required follow-ups. It provides the audit trail for client relationship management and ensures continuity across team members.",
    semanticTags = listOf("client-relations", "interaction", "crm", "communication"),
    attributes = mapOf(
        "subject" to CoreModelAttribute(
            key = "subject", schemaType = "TEXT", label = "Subject", dataType = "string",
            semantics = AttributeSemantics(
                definition = "The subject or topic of the communication.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "topic"),
            ),
        ),
        "type" to CoreModelAttribute(
            key = "type", schemaType = "SELECT", label = "Type", dataType = "string",
            options = AttributeOptions(enum = listOf("meeting", "call", "email", "presentation", "workshop")),
            semantics = AttributeSemantics(
                definition = "The format or medium of this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("format", "channel"),
            ),
        ),
        "direction" to CoreModelAttribute(
            key = "direction", schemaType = "SELECT", label = "Direction", dataType = "string",
            options = AttributeOptions(enum = listOf("inbound", "outbound")),
            semantics = AttributeSemantics(
                definition = "Whether this communication was initiated by the client or by the team.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("direction", "initiative"),
            ),
        ),
        "date" to CoreModelAttribute(
            key = "date", schemaType = "DATE", label = "Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "The date of the communication.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("timeline"),
            ),
        ),
        "summary" to CoreModelAttribute(
            key = "summary", schemaType = "TEXT", label = "Summary", dataType = "string",
            semantics = AttributeSemantics(
                definition = "A summary of what was discussed or communicated.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("notes", "context"),
            ),
        ),
        "follow-up-date" to CoreModelAttribute(
            key = "follow-up-date", schemaType = "DATE", label = "Follow-up Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "The date by which a follow-up action is expected.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("action-item", "deadline"),
            ),
        ),
        "outcome" to CoreModelAttribute(
            key = "outcome", schemaType = "SELECT", label = "Outcome", dataType = "string",
            options = AttributeOptions(enum = listOf("positive", "neutral", "needs-action", "escalated")),
            semantics = AttributeSemantics(
                definition = "The outcome or disposition of this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("result", "disposition"),
            ),
        ),
        "channel" to CoreModelAttribute(
            key = "channel", schemaType = "SELECT", label = "Channel", dataType = "string",
            options = AttributeOptions(enum = listOf("phone", "video", "in-person", "email", "chat")),
            semantics = AttributeSemantics(
                definition = "The specific channel used for this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("channel", "medium"),
            ),
        ),
    ),
)
