package riven.core.lifecycle.models.saas

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.lifecycle.AttributeOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition
import riven.core.lifecycle.models.base.CommunicationBase

/**
 * SaaS Communication — a logged interaction with a SaaS customer.
 * Includes demo and presentation types with follow-up date tracking.
 */
object SaasCommunicationModel : CoreModelDefinition(
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
    attributes = CommunicationBase.attributes + mapOf(
        "type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Type", dataType = DataType.STRING,
            options = AttributeOptions(enum = listOf("meeting", "call", "email", "demo", "presentation")),
            semantics = AttributeSemantics(
                definition = "The format or medium of this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("format", "channel"),
            ),
        ),
        "follow-up-date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Follow-up Date", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "The date by which a follow-up action is expected.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("action-item", "deadline"),
            ),
        ),
    ),
)
