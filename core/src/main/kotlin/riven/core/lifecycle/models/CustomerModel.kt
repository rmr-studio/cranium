package riven.core.lifecycle.models

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.lifecycle.*

/**
 * Customer — the central entity in the customer lifecycle.
 * Spans all lifecycle domains (UNCATEGORIZED). Hub entity that other core models relate to.
 */
object CustomerModel : CoreModelDefinition(
    key = "customer",
    displayNameSingular = "Customer",
    displayNamePlural = "Customers",
    iconType = IconType.USERS,
    iconColour = IconColour.BLUE,
    semanticGroup = SemanticGroup.CUSTOMER,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    identifierKey = "email",
    semanticDefinition = "A customer represents a person or organisation that has a commercial relationship with the business. Customers are the central entity around which revenue, support, and engagement activities are organised.",
    semanticTags = listOf("crm", "contact", "revenue", "lifecycle"),
    attributes = mapOf(
        "name" to CoreModelAttribute(
            key = "name", schemaType = "TEXT", label = "Name", dataType = "string",
            required = true,
            semantics = AttributeSemantics(
                definition = "The full name of the customer, used as the primary human-readable identifier in listings and communications.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("contact", "display-name"),
            ),
        ),
        "email" to CoreModelAttribute(
            key = "email", schemaType = "EMAIL", label = "Email", dataType = "string",
            format = "email", required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "The primary email address for the customer, used for communication, login identification, and deduplication.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("contact", "unique-key", "communication"),
            ),
        ),
        "phone" to CoreModelAttribute(
            key = "phone", schemaType = "PHONE", label = "Phone", dataType = "string",
            format = "phone-number",
            semantics = AttributeSemantics(
                definition = "The customer's phone number for direct contact, support escalation, or SMS communication.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("contact", "communication"),
            ),
        ),
        "company" to CoreModelAttribute(
            key = "company", schemaType = "TEXT", label = "Company", dataType = "string",
            semantics = AttributeSemantics(
                definition = "The name of the organisation or business the customer is associated with, used for B2B segmentation and account grouping.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("organisation", "segmentation"),
            ),
        ),
        "status" to CoreModelAttribute(
            key = "status", schemaType = "SELECT", label = "Status", dataType = "string",
            required = true,
            options = AttributeOptions(enum = listOf("active", "inactive", "churned"), default = "active"),
            semantics = AttributeSemantics(
                definition = "The current lifecycle status of the customer, indicating whether they are actively engaged, dormant, or have left.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "segmentation", "health"),
            ),
        ),
        "source" to CoreModelAttribute(
            key = "source", schemaType = "SELECT", label = "Source", dataType = "string",
            options = AttributeOptions(enum = listOf("organic", "referral", "paid", "partner")),
            semantics = AttributeSemantics(
                definition = "The acquisition channel through which the customer was first obtained, used for attribution and marketing analysis.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("acquisition", "attribution", "marketing"),
            ),
        ),
        "created-date" to CoreModelAttribute(
            key = "created-date", schemaType = "DATE", label = "Created Date", dataType = "string",
            format = "date",
            semantics = AttributeSemantics(
                definition = "The date the customer record was first created, marking the start of the business relationship.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("lifecycle", "onboarding"),
            ),
        ),
        "notes" to CoreModelAttribute(
            key = "notes", schemaType = "TEXT", label = "Notes", dataType = "string",
            semantics = AttributeSemantics(
                definition = "Free-form notes about the customer, capturing context, preferences, or history that does not fit structured fields.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("context", "internal"),
            ),
        ),
    ),
    relationships = listOf(
        CoreModelRelationship(
            key = "customer-support-tickets",
            name = "Support Tickets",
            sourceModelKey = "customer",
            targetModelKey = "support-ticket",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Customer",
            semantics = RelationshipSemantics(
                definition = "A customer files support tickets for issues or questions.",
                tags = listOf("support", "service"),
            ),
        ),
        CoreModelRelationship(
            key = "customer-acquisition-source",
            name = "Acquisition Source",
            sourceModelKey = "customer",
            targetModelKey = "acquisition-source",
            cardinality = EntityRelationshipCardinality.MANY_TO_ONE,
            inverseName = "Customers",
            semantics = RelationshipSemantics(
                definition = "The acquisition channel through which the customer signed up.",
                tags = listOf("attribution", "marketing"),
            ),
        ),
        CoreModelRelationship(
            key = "customer-billing-events",
            name = "Billing Events",
            sourceModelKey = "customer",
            targetModelKey = "billing-event",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Customer",
            semantics = RelationshipSemantics(
                definition = "Billing events associated with a customer's account.",
                tags = listOf("billing", "financial"),
            ),
        ),
        CoreModelRelationship(
            key = "customer-churn-events",
            name = "Churn Events",
            sourceModelKey = "customer",
            targetModelKey = "churn-event",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Customer",
            semantics = RelationshipSemantics(
                definition = "Churn events marking the end of a customer relationship.",
                tags = listOf("churn", "retention"),
            ),
        ),
        CoreModelRelationship(
            key = "customer-communications",
            name = "Communications",
            sourceModelKey = "customer",
            targetModelKey = "communication",
            cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
            inverseName = "Customer",
            semantics = RelationshipSemantics(
                definition = "Communications exchanged with a customer.",
                tags = listOf("communication", "interaction"),
            ),
        ),
    ),
)
