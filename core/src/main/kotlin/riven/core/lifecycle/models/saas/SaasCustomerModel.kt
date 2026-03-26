package riven.core.lifecycle.models.saas

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.lifecycle.AttributeSemantics
import riven.core.lifecycle.CoreModelAttribute
import riven.core.lifecycle.CoreModelDefinition
import riven.core.lifecycle.models.base.CustomerBase

/**
 * SaaS Customer — the central entity in the B2C SaaS customer lifecycle.
 * Extends the universal customer with a company field for organisational context.
 */
object SaasCustomerModel : CoreModelDefinition(
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
    attributes = CustomerBase.attributes + mapOf(
        "company" to CoreModelAttribute(
            schemaType = SchemaType.TEXT, label = "Company", dataType = DataType.STRING,
            semantics = AttributeSemantics(
                definition = "The name of the organisation or business the customer is associated with, used for B2B segmentation and account grouping.",
                classification = SemanticAttributeClassification.CATEGORICAL,
                tags = listOf("organisation", "segmentation"),
            ),
        ),
    ),
    relationships = CustomerBase.relationships,
)
