package riven.core.service.util.factory

import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.catalog.*
import java.util.*

object CatalogTestFactory {

    fun createCatalogEntityType(
        key: String,
        singular: String,
        plural: String,
        schema: Map<String, Any> = mapOf(
            "name" to mapOf<String, Any>("key" to "TEXT", "label" to "Name", "type" to "string", "required" to true),
        ),
        identifierKey: String? = "name",
        semanticMetadata: List<CatalogSemanticMetadataModel> = emptyList(),
    ) = CatalogEntityTypeModel(
        id = UUID.randomUUID(),
        key = key,
        displayNameSingular = singular,
        displayNamePlural = plural,
        iconType = IconType.CIRCLE_DASHED,
        iconColour = IconColour.NEUTRAL,
        semanticGroup = SemanticGroup.CUSTOMER,
        identifierKey = identifierKey,
        readonly = false,
        schema = schema,
        columns = null,
        semanticMetadata = semanticMetadata,
    )

    fun createManifestWithEntityTypes(
        vararg entityTypes: CatalogEntityTypeModel,
        key: String = "test-template",
        name: String = "Test Template",
    ) = ManifestDetail(
        id = UUID.randomUUID(),
        key = key,
        name = name,
        description = "A test template",
        manifestType = ManifestType.TEMPLATE,
        manifestVersion = "1.0",
        entityTypes = entityTypes.toList(),
        relationships = emptyList(),
        fieldMappings = emptyList(),
    )

    fun createManifestWithRelationship(): ManifestDetail {
        val customer = createCatalogEntityType("customer", "Customer", "Customers")
        val order = createCatalogEntityType("order", "Order", "Orders")
        val relationship = CatalogRelationshipModel(
            id = UUID.randomUUID(),
            key = "customer-orders-rel",
            sourceEntityTypeKey = "customer",
            name = "customer-orders",
            iconType = IconType.LINK,
            iconColour = IconColour.NEUTRAL,
            cardinalityDefault = EntityRelationshipCardinality.ONE_TO_MANY,
            `protected` = false,
            targetRules = listOf(
                CatalogRelationshipTargetRuleModel(
                    id = UUID.randomUUID(),
                    targetEntityTypeKey = "order",
                    cardinalityOverride = null,
                    inverseVisible = true,
                    inverseName = "Orders",
                )
            ),
        )
        return ManifestDetail(
            id = UUID.randomUUID(),
            key = "test-template",
            name = "Test Template",
            description = "A test template",
            manifestType = ManifestType.TEMPLATE,
            manifestVersion = "1.0",
            entityTypes = listOf(customer, order),
            relationships = listOf(relationship),
            fieldMappings = emptyList(),
        )
    }
}
