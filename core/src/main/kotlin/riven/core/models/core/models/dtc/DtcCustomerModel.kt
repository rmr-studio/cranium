package cranium.core.models.core.models.dtc

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.core.ProjectionAcceptRule
import cranium.core.models.core.base.CustomerBase

/**
 * DTC Customer — the central entity in the direct-to-consumer ecommerce lifecycle.
 * Uses only universal customer attributes (no company field).
 */
object DtcCustomerModel : cranium.core.models.core.CoreModelDefinition(
    key = "customer",
    displayNameSingular = "Customer",
    displayNamePlural = "Customers",
    iconType = IconType.USERS,
    iconColour = IconColour.BLUE,
    semanticGroup = SemanticGroup.CUSTOMER,
    lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
    role = EntityTypeRole.CATALOG,
    identifierKey = "email",
    semanticDefinition = "A customer represents a person who purchases directly from the business. Customers are the central entity around which orders, support, and engagement activities are organised.",
    semanticTags = listOf("crm", "contact", "revenue", "lifecycle"),
    attributes = CustomerBase.attributes,
    relationships = CustomerBase.relationships,
    projectionAccepts = listOf(
        ProjectionAcceptRule(
            domain = LifecycleDomain.UNCATEGORIZED,
            semanticGroup = SemanticGroup.CUSTOMER,
            relationshipName = ProjectionAcceptRule.SOURCE_DATA_RELATIONSHIP,
        ),
    ),
)
