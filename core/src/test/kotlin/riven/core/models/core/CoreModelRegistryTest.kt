package cranium.core.models.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import cranium.core.enums.catalog.ManifestType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.EntityTypeRole
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.core.models.OrderLineItemModel
import cranium.core.models.core.models.ProductModel
import cranium.core.models.core.base.BillingEventBase
import cranium.core.models.core.base.ChurnEventBase
import cranium.core.models.core.base.CommunicationModel
import cranium.core.models.core.base.CustomerBase


class CoreModelRegistryTest {

    @Test
    fun `allModels validates and loads without error`() {
        assertDoesNotThrow { CoreModelRegistry.allModels }
    }

    @Test
    fun `allModels contains all DTC models`() {
        val models = CoreModelRegistry.allModels
        assertTrue(models.any { it.key == "customer" }, "Should contain customer")
        assertTrue(models.any { it.key == "support-ticket" }, "Should contain support-ticket")
        assertTrue(models.any { it.key == "order" }, "Should contain order")
        assertTrue(models.any { it.key == "campaign" }, "Should contain campaign")
        assertTrue(models.any { it.key == "shipment" }, "Should contain shipment")
    }

    // ------ B2C_SAAS rip regressions ------

    @Test
    fun `findModelSet returns null for removed b2c-saas key`() {
        assertNull(CoreModelRegistry.findModelSet("b2c-saas"))
    }

    @Test
    fun `allResolvedManifests includes dtc-ecommerce and knowledge`() {
        val manifests = CoreModelRegistry.allResolvedManifests()
        val keys = manifests.map { it.key }
        assertTrue(keys.contains("dtc-ecommerce"), "Should contain dtc-ecommerce manifest")
        assertTrue(keys.contains("knowledge"), "Should contain knowledge manifest")
    }

    @Test
    fun `KNOWLEDGE_MODELS is included in resolved manifests with KNOWLEDGE role`() {
        val manifests = CoreModelRegistry.allResolvedManifests()
        val knowledge = manifests.first { it.key == "knowledge" }
        val keys = knowledge.entityTypes.map { it.key }.toSet()
        assertTrue(keys.contains("note"), "knowledge manifest should contain note")
        assertTrue(keys.contains("glossary"), "knowledge manifest should contain glossary")
        assertTrue(
            knowledge.entityTypes.all { it.role == EntityTypeRole.KNOWLEDGE },
            "all knowledge entity types should have KNOWLEDGE role",
        )
    }
    // ------ Model migration regressions ------

    @Test
    fun `ProductModel lifecycleDomain is COMMERCE`() {
        assertEquals(LifecycleDomain.COMMERCE, ProductModel.lifecycleDomain)
        val rule = ProductModel.projectionAccepts.single()
        assertEquals(LifecycleDomain.COMMERCE, rule.domain)
    }

    @Test
    fun `OrderLineItemModel semanticGroup is LINE_ITEM`() {
        assertEquals(SemanticGroup.LINE_ITEM, OrderLineItemModel.semanticGroup)
        val rule = OrderLineItemModel.projectionAccepts.single()
        assertEquals(SemanticGroup.LINE_ITEM, rule.semanticGroup)
    }


    // ------ DTC set sanity ------

    @Test
    fun `findModelSet returns correct set for dtc-ecommerce`() {
        val set = requireNotNull(CoreModelRegistry.findModelSet("dtc-ecommerce")) {
            "dtc-ecommerce model set should exist"
        }
        assertEquals("DTC E-commerce", set.name)
        assertTrue(set.models.any { it.key == "order" })
        assertFalse(set.models.any { it.key == "subscription" })
    }

    @Test
    fun `findModelSet returns null for unknown key`() {
        assertNull(CoreModelRegistry.findModelSet("unknown"))
    }

    @Test
    fun `DTC customer does NOT have company attribute`() {
        val dtcCustomer = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "customer")) {
            "DTC customer model should exist"
        }
        assertFalse(dtcCustomer.attributes.containsKey("company"),
            "DTC customer should NOT have company attribute")
    }

    @Test
    fun `DTC billing event has ecommerce-specific types`() {
        val dtcBilling = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "billing-event"))
        val dtcTypeEnum = extractEnumValues(dtcBilling, "type")
        assertTrue(dtcTypeEnum.contains("shipping-fee"), "DTC billing should have shipping-fee")
        assertFalse(dtcTypeEnum.contains("trial-start"), "DTC billing should NOT have trial-start")
    }

    @Test
    fun `DTC churn event has revenue-lost`() {
        val dtcChurn = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "churn-event"))
        assertTrue(dtcChurn.attributes.containsKey("revenue-lost"), "DTC churn should have revenue-lost")
        assertFalse(dtcChurn.attributes.containsKey("mrr-lost"), "DTC churn should NOT have mrr-lost")
    }

    @Test
    fun `DTC churn reasons include ecommerce reasons`() {
        val dtcChurn = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "churn-event"))
        val dtcReasons = extractEnumValues(dtcChurn, "reason")
        assertTrue(dtcReasons.contains("product-quality"))
        assertTrue(dtcReasons.contains("shipping-issues"))
        assertTrue(dtcReasons.contains("sizing-issues"))
    }

    @Test
    fun `DTC communication types include sms and social-media`() {
        val dtcComm = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "communication"))
        val dtcTypes = extractEnumValues(dtcComm, "type")
        assertTrue(dtcTypes.contains("sms"))
        assertTrue(dtcTypes.contains("social-media"))
        assertFalse(dtcComm.attributes.containsKey("follow-up-date"),
            "DTC communication should NOT have follow-up-date")
    }

    // ------ Base attribute sharing ------

    @Test
    fun `DTC billing-event contains base attributes`() {
        val dtcBilling = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "billing-event"))
        for (baseKey in BillingEventBase.attributes.keys) {
            assertTrue(dtcBilling.attributes.containsKey(baseKey),
                "DTC billing event should contain base attribute '$baseKey'")
        }
    }

    @Test
    fun `DTC churn-event contains base attributes`() {
        val dtcChurn = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "churn-event"))
        for (baseKey in ChurnEventBase.attributes.keys) {
            assertTrue(dtcChurn.attributes.containsKey(baseKey),
                "DTC churn event should contain base attribute '$baseKey'")
        }
    }

    @Test
    fun `DTC customer contains base attributes`() {
        val dtcCustomer = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "customer"))
        for (baseKey in CustomerBase.attributes.keys) {
            assertTrue(dtcCustomer.attributes.containsKey(baseKey),
                "DTC customer should contain base attribute '$baseKey'")
        }
    }

    @Test
    fun `DTC communication contains base attributes`() {
        val dtcComm = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "communication"))
        for (baseKey in CommunicationModel.attributes.keys) {
            assertTrue(dtcComm.attributes.containsKey(baseKey),
                "DTC communication should contain base attribute '$baseKey'")
        }
    }

    // ------ Manifest conversion ------

    @Test
    fun `toResolvedManifest produces valid manifest for dtc-ecommerce`() {
        val modelSet = requireNotNull(CoreModelRegistry.findModelSet("dtc-ecommerce")) {
            "dtc-ecommerce model set should exist"
        }
        val manifest = CoreModelRegistry.toResolvedManifest(modelSet)

        assertEquals("dtc-ecommerce", manifest.key)
        assertEquals(ManifestType.TEMPLATE, manifest.type)
        assertFalse(manifest.stale)
        assertTrue(manifest.entityTypes.isNotEmpty())
        assertTrue(manifest.relationships.isNotEmpty())
        assertTrue(manifest.fieldMappings.isEmpty())
        assertEquals(modelSet.models.size, manifest.entityTypes.size)

        val customer = requireNotNull(manifest.entityTypes.find { it.key == "customer" })
        assertEquals("Customer", customer.displayNameSingular)
        assertEquals("CUSTOMER", customer.semanticGroup)
        assertEquals(LifecycleDomain.UNCATEGORIZED, customer.lifecycleDomain)
        assertFalse(customer.readonly)
        assertTrue(customer.schema.containsKey("email"))
    }

    @Test
    fun `toResolvedManifest includes both model and additional relationships`() {
        val modelSet = requireNotNull(CoreModelRegistry.findModelSet("dtc-ecommerce"))
        val manifest = CoreModelRegistry.toResolvedManifest(modelSet)

        // Model-declared relationships (from DtcCustomerModel via CustomerBase)
        val supportTicketRel = manifest.relationships.find { it.key == "customer-support-tickets" }
        assertNotNull(supportTicketRel, "Should include model-declared relationship")

        // Additional relationships
        val orderRel = manifest.relationships.find { it.key == "customer-orders" }
        assertNotNull(orderRel, "Should include additional model set relationship")

        // New relationships introduced in this plan
        assertNotNull(manifest.relationships.find { it.key == "product-variants" })
        assertNotNull(manifest.relationships.find { it.key == "order-shipments" })
        assertNotNull(manifest.relationships.find { it.key == "campaign-creatives" })
        assertNotNull(manifest.relationships.find { it.key == "acquisition-source-campaign" })
    }

    /**
     * Regression: CoreModelRegistry previously only validated targetModelKey for additional
     * relationships, allowing a typo in sourceModelKey to pass validation silently.
     */
    @Test
    fun `validateModelSetRelationships rejects additional relationship with invalid sourceModelKey`() {
        val stubModel = object : CoreModelDefinition(
            key = "stub-a",
            displayNameSingular = "Stub A",
            displayNamePlural = "Stub As",
            identifierKey = "name",
            attributes = mapOf(
                "name" to CoreModelAttribute(
                    schemaType = SchemaType.TEXT, label = "Name", dataType = DataType.STRING,
                )
            ),
        ) {}

        val modelSet = CoreModelSet(
            manifestKey = "test-set",
            name = "Test",
            description = "Test model set",
            models = listOf(stubModel),
            additionalRelationships = listOf(
                CoreModelRelationship(
                    key = "bad-rel",
                    name = "Bad Relationship",
                    sourceModelKey = "nonexistent-model",
                    targetModelKey = "stub-a",
                    cardinality = EntityRelationshipCardinality.ONE_TO_MANY,
                )
            )
        )

        val ex = assertThrows<IllegalStateException> {
            CoreModelRegistry.validateModelSetRelationships(modelSet)
        }
        assertTrue(ex.message!!.contains("nonexistent-model"))
    }

    // ------ Collision gate (CRITICAL, per IRON RULE) ------

    /**
     * Parameterized check: every (domain, group) tuple declared by a projectionAccepts rule
     * must resolve to exactly one core model. Guards against future model additions that
     * silently reintroduce projection routing collisions.
     */
    @Test
    fun `no two core models accept the same domain-group tuple`() {
        val colliders = CoreModelRegistry.allModels
            .flatMap { model ->
                model.projectionAccepts.map { rule -> (rule.domain to rule.semanticGroup) to model.key }
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }

        assertTrue(colliders.isEmpty()) {
            "Projection rule collisions:\n" +
                colliders.entries.joinToString("\n") { (tuple, keys) -> "  $tuple -> $keys" }
        }
    }

    // ------ Helpers ------

    @Suppress("UNCHECKED_CAST")
    private fun extractEnumValues(model: CoreModelDefinition, attributeKey: String): List<String> {
        val attr = requireNotNull(model.attributes[attributeKey]) {
            "Model '${model.key}' should have attribute '$attributeKey'"
        }
        return requireNotNull(attr.options?.enum) {
            "Attribute '$attributeKey' on model '${model.key}' should have enum options"
        }
    }
}
