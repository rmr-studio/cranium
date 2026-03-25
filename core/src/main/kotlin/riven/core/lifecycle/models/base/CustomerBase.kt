package riven.core.lifecycle.models.base

import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.lifecycle.AttributeOptions
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelRelationship
import riven.core.lifecycle.RelationshipSemantics

/**
 * Shared attributes and relationships for the Customer model across all business types.
 * Business-type variants compose these with type-specific additions.
 */
object CustomerBase {

    val attributes = mapOf(
        "name" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Name", dataType = DataType.STRING,
            required = true,
            semantics = AttributeSemantics(
                definition = "The full name of the customer, used as the primary human-readable identifier in listings and communications.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("contact", "display-name"),
            ),
        ),
        "email" to CoreModelAttribute(
            schemaType = SchemaType.EMAIL, label = "Email", dataType = DataType.STRING,
            format = "email", required = true, unique = true,
            semantics = AttributeSemantics(
                definition = "The primary email address for the customer, used for communication, login identification, and deduplication.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("contact", "unique-key", "communication"),
            ),
        ),
        "phone" to CoreModelAttribute(
            schemaType = SchemaType.PHONE, label = "Phone", dataType = DataType.STRING,
            format = "phone-number",
            semantics = AttributeSemantics(
                definition = "The customer's phone number for direct contact, support escalation, or SMS communication.",
                classification = SemanticAttributeClassification.IDENTIFIER,
                tags = listOf("contact", "communication"),
            ),
        ),
        "status" to CoreModelAttribute(
            schemaType = SchemaType.SELECT, label = "Status", dataType = DataType.STRING,
            required = true,
            options = AttributeOptions(enum = listOf("active", "inactive", "churned"), default = "active"),
            semantics = AttributeSemantics(
                definition = "The current lifecycle status of the customer, indicating whether they are actively engaged, dormant, or have left.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("lifecycle", "segmentation", "health"),
            ),
        ),
        "created-date" to CoreModelAttribute(
            schemaType = SchemaType.DATE, label = "Created Date", dataType = DataType.STRING,
            format = "date",
            semantics = AttributeSemantics(
                definition = "The date the customer record was first created, marking the start of the business relationship.",
                classification = SemanticAttributeClassification.TEMPORAL,
                tags = listOf("lifecycle", "onboarding"),
            ),
        ),
        "notes" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Notes", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "Free-form notes about the customer, capturing context, preferences, or history that does not fit structured fields.",
                classification = SemanticAttributeClassification.FREETEXT,
                tags = listOf("context", "internal"),
            ),
        ),
    )

    val relationships = listOf(
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
    )
}
