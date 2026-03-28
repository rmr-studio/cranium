package riven.core.lifecycle.models.dtc

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
 * DTC Communication — a logged interaction with a DTC ecommerce customer.
 * Includes SMS and social media channels. No follow-up date tracking.
 */
object DtcCommunicationModel : CoreModelDefinition(
    key = "communication",
    displayNameSingular = "Communication",
    displayNamePlural = "Communications",
    iconType = IconType.MESSAGE_SQUARE,
    iconColour = IconColour.TEAL,
    semanticGroup = SemanticGroup.COMMUNICATION,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    identifierKey = "subject",
    semanticDefinition = "A communication logs a customer interaction, capturing what was discussed, the outcome, and any follow-up actions. Provides the audit trail for customer relationship management across channels.",
    semanticTags = listOf("customer-relations", "interaction", "crm", "communication"),
    attributes = CommunicationBase.attributes + mapOf(
        "type" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Type", dataType = DataType.STRING,
            options = AttributeOptions(enum = listOf("meeting", "call", "email", "sms", "social-media")),
            semantics = AttributeSemantics(
                definition = "The format or medium of this communication.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("format", "channel"),
            ),
        ),
    ),
)
