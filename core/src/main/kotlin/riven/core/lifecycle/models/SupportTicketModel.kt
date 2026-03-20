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
 * Support Ticket — tracks customer issues, questions, and feedback through resolution.
 */
object SupportTicketModel : CoreModelDefinition(
    key = "support-ticket",
    displayNameSingular = "Support Ticket",
    displayNamePlural = "Support Tickets",
    iconType = IconType.TICKET,
    iconColour = IconColour.ORANGE,
    semanticGroup = SemanticGroup.SUPPORT,
    lifecycleDomain = LifecycleDomain.SUPPORT,
    identifierKey = "subject",
    semanticDefinition = "A support ticket tracks a customer issue, question, or piece of feedback through its lifecycle from creation to resolution. It is the primary unit of work for customer support operations and feeds into service quality metrics.",
    semanticTags = listOf("support", "service", "customer-success", "operations"),
    attributes = mapOf(
        "subject" to CoreModelAttribute(
            key = "subject", schemaType = "TEXT", label = "Subject", dataType = "string",
            required = true,
            semantics = AttributeSemantics(
                definition = "A brief summary of the support request, serving as the primary human-readable identifier for the ticket.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("display-name", "search"),
            ),
        ),
        "description" to CoreModelAttribute(
            key = "description", schemaType = "TEXT", label = "Description", dataType = "string",
            semantics = AttributeSemantics(
                definition = "A detailed account of the issue, question, or feedback provided by the customer or support agent.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("detail", "context"),
            ),
        ),
        "priority" to CoreModelAttribute(
            key = "priority", schemaType = "SELECT", label = "Priority", dataType = "string",
            required = true,
            options = AttributeOptions(enum = listOf("low", "medium", "high", "critical"), default = "medium"),
            semantics = AttributeSemantics(
                definition = "The urgency level of the support request, determining SLA targets and queue ordering.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("triage", "sla", "urgency"),
            ),
        ),
        "status" to CoreModelAttribute(
            key = "status", schemaType = "SELECT", label = "Status", dataType = "string",
            required = true,
            options = AttributeOptions(enum = listOf("open", "in-progress", "resolved", "closed"), default = "open"),
            semantics = AttributeSemantics(
                definition = "The current resolution state of the ticket, tracking progress from initial report through to closure.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "workflow"),
            ),
        ),
        "channel" to CoreModelAttribute(
            key = "channel", schemaType = "SELECT", label = "Channel", dataType = "string",
            options = AttributeOptions(enum = listOf("email", "chat", "phone", "web")),
            semantics = AttributeSemantics(
                definition = "The communication channel through which the support request was initially received.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("source", "communication"),
            ),
        ),
        "created-date" to CoreModelAttribute(
            key = "created-date", schemaType = "DATE", label = "Created Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "The date the support ticket was created, marking the start of the SLA clock.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("sla", "timeline"),
            ),
        ),
        "resolved-date" to CoreModelAttribute(
            key = "resolved-date", schemaType = "DATE", label = "Resolved Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "The date the support ticket was resolved, used to calculate resolution time and SLA compliance.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("sla", "resolution"),
            ),
        ),
        "category" to CoreModelAttribute(
            key = "category", schemaType = "SELECT", label = "Category", dataType = "string",
            options = AttributeOptions(enum = listOf("billing", "technical", "general", "feature-request")),
            semantics = AttributeSemantics(
                definition = "The type of issue or request, used for routing, trend analysis, and workload distribution.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("routing", "analysis", "classification"),
            ),
        ),
    ),
)
