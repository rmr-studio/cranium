package riven.core.lifecycle.models.dtc

import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.lifecycle.CoreModelDefinition
import riven.core.lifecycle.models.base.CustomerBase

/**
 * DTC Customer — the central entity in the direct-to-consumer ecommerce lifecycle.
 * Uses only universal customer attributes (no company field).
 */
object DtcCustomerModel : CoreModelDefinition(
    key = "customer",
    displayNameSingular = "Customer",
    displayNamePlural = "Customers",
    iconType = IconType.USERS,
    iconColour = IconColour.BLUE,
    semanticGroup = SemanticGroup.CUSTOMER,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    identifierKey = "email",
    semanticDefinition = "A customer represents a person who purchases directly from the business. Customers are the central entity around which orders, support, and engagement activities are organised.",
    semanticTags = listOf("crm", "contact", "revenue", "lifecycle"),
    attributes = CustomerBase.attributes,
    relationships = CustomerBase.relationships,
)
