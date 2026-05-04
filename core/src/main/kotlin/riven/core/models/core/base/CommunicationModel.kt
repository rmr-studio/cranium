package riven.core.models.core.base

import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.CommunicationType
import riven.core.enums.core.DataType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.enums.entity.EntityTypeRole
import riven.core.models.common.validation.DefaultValue
import riven.core.models.common.validation.SchemaOptions
import riven.core.models.core.AttributeSemantics
import riven.core.models.core.CoreModelAttribute

/**
 * Shared attributes for the Communication model across all business types.
 * The `type` enum and `follow-up-date` presence differ per business type.
 */

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.core.ProjectionAcceptRule

/**
   Logged communication interactions (Internal + External).
    - Social Media Conversation
    - Phone Call
    - Email
    - SMS
    - Video Call
    - Internal Meeting
 */
object CommunicationModel : riven.core.models.core.CoreModelDefinition(
    key = "communication",
    role = EntityTypeRole.KNOWLEDGE,
    displayNameSingular = "Communication",
    displayNamePlural = "Communications",
    iconType = IconType.MESSAGE_SQUARE,
    iconColour = IconColour.TEAL,
    semanticGroup = SemanticGroup.COMMUNICATION,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    identifierKey = "subject",
    semanticDefinition = "A communication logs a customer interaction, capturing what was discussed, the outcome, and any follow-up actions. Provides the audit trail for customer relationship management across channels.",
    semanticTags = listOf("customer-relations", "interaction", "crm", "communication"),
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.UNCATEGORIZED,
            semanticGroup = SemanticGroup.COMMUNICATION,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
    attributes = mapOf(
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
        "type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Type", dataType = DataType.STRING,
            options = SchemaOptions(enum = CommunicationType.entries.map { it.value }),
            semantics = AttributeSemantics(
                definition = "The format or medium of this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("format", "channel"),
            ),
        )
))


