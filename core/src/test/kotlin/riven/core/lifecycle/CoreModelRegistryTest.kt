package riven.core.lifecycle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.LifecycleDomain
import riven.core.lifecycle.models.base.BillingEventBase
import riven.core.lifecycle.models.base.ChurnEventBase
import riven.core.lifecycle.models.base.CommunicationBase
import riven.core.lifecycle.models.base.CustomerBase

class CoreModelRegistryTest {

    @Test
    fun `allModels validates and loads without error`() {
        assertDoesNotThrow { CoreModelRegistry.allModels }
    }

    @Test
    fun `allModels returns all unique model object instances`() {
        val models = CoreModelRegistry.allModels
        // With business-type variants, there are more model objects than unique keys
        // (e.g., SaasBillingEventModel and DtcBillingEventModel both use key "billing-event")
        assertTrue(models.size > models.map { it.key }.distinct().size,
            "Should have more model objects than unique keys due to business-type variants")
        assertTrue(models.any { it.key == "customer" }, "Should contain customer")
        assertTrue(models.any { it.key == "support-ticket" }, "Should contain support-ticket")
    }

    @Test
    fun `findModelSet returns correct set for b2c-saas`() {
        val set = requireNotNull(CoreModelRegistry.findModelSet("b2c-saas")) {
            "b2c-saas model set should exist"
        }
        assertEquals("B2C SaaS", set.name)
        assertTrue(set.models.any { it.key == "subscription" })
        assertFalse(set.models.any { it.key == "order" })
    }

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
    fun `findModel returns correct variant for model set`() {
        val saasCustomer = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "customer")) {
            "SaaS customer model should exist"
        }
        assertEquals("Customer", saasCustomer.displayNameSingular)
        assertTrue(saasCustomer.attributes.containsKey("company"),
            "SaaS customer should have company attribute")

        val dtcCustomer = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "customer")) {
            "DTC customer model should exist"
        }
        assertFalse(dtcCustomer.attributes.containsKey("company"),
            "DTC customer should NOT have company attribute")
    }

    // ------ Business-type tailoring tests ------

    @Test
    fun `SaaS billing event includes trial types, DTC does not`() {
        val saasBilling = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "billing-event"))
        val dtcBilling = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "billing-event"))

        val saasTypeEnum = extractEnumValues(saasBilling, "type")
        assertTrue(saasTypeEnum.contains("trial-start"), "SaaS billing should have trial-start")
        assertTrue(saasTypeEnum.contains("trial-end"), "SaaS billing should have trial-end")

        val dtcTypeEnum = extractEnumValues(dtcBilling, "type")
        assertFalse(dtcTypeEnum.contains("trial-start"), "DTC billing should NOT have trial-start")
        assertFalse(dtcTypeEnum.contains("trial-end"), "DTC billing should NOT have trial-end")
        assertFalse(dtcTypeEnum.contains("purchase"), "DTC billing should NOT have purchase (use OrderModel)")
        assertTrue(dtcTypeEnum.contains("shipping-fee"), "DTC billing should have shipping-fee")
    }

    @Test
    fun `SaaS churn event has mrr-lost, DTC has revenue-lost`() {
        val saasChurn = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "churn-event"))
        val dtcChurn = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "churn-event"))

        assertTrue(saasChurn.attributes.containsKey("mrr-lost"), "SaaS churn should have mrr-lost")
        assertFalse(saasChurn.attributes.containsKey("revenue-lost"), "SaaS churn should NOT have revenue-lost")

        assertTrue(dtcChurn.attributes.containsKey("revenue-lost"), "DTC churn should have revenue-lost")
        assertFalse(dtcChurn.attributes.containsKey("mrr-lost"), "DTC churn should NOT have mrr-lost")
    }

    @Test
    fun `SaaS churn reasons include product-specific reasons, DTC includes ecommerce reasons`() {
        val saasChurn = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "churn-event"))
        val dtcChurn = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "churn-event"))

        val saasReasons = extractEnumValues(saasChurn, "reason")
        assertTrue(saasReasons.contains("missing-feature"), "SaaS churn should have missing-feature reason")
        assertTrue(saasReasons.contains("onboarding-failure"), "SaaS churn should have onboarding-failure reason")

        val dtcReasons = extractEnumValues(dtcChurn, "reason")
        assertTrue(dtcReasons.contains("product-quality"), "DTC churn should have product-quality reason")
        assertTrue(dtcReasons.contains("shipping-issues"), "DTC churn should have shipping-issues reason")
        assertTrue(dtcReasons.contains("sizing-issues"), "DTC churn should have sizing-issues reason")
        assertFalse(dtcReasons.contains("missing-feature"), "DTC churn should NOT have missing-feature reason")
    }

    @Test
    fun `SaaS communication includes follow-up-date, DTC does not`() {
        val saasComm = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "communication"))
        val dtcComm = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "communication"))

        assertTrue(saasComm.attributes.containsKey("follow-up-date"),
            "SaaS communication should have follow-up-date")
        assertFalse(dtcComm.attributes.containsKey("follow-up-date"),
            "DTC communication should NOT have follow-up-date")
    }

    @Test
    fun `SaaS communication has demo type, DTC has sms and social-media`() {
        val saasComm = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "communication"))
        val dtcComm = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "communication"))

        val saasTypes = extractEnumValues(saasComm, "type")
        assertTrue(saasTypes.contains("demo"), "SaaS communication should have demo type")
        assertFalse(saasTypes.contains("sms"), "SaaS communication should NOT have sms type")

        val dtcTypes = extractEnumValues(dtcComm, "type")
        assertTrue(dtcTypes.contains("sms"), "DTC communication should have sms type")
        assertTrue(dtcTypes.contains("social-media"), "DTC communication should have social-media type")
        assertFalse(dtcTypes.contains("demo"), "DTC communication should NOT have demo type")
    }

    // ------ Base attribute sharing tests ------

    @Test
    fun `all variants of billing-event contain base attributes`() {
        val saasBilling = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "billing-event"))
        val dtcBilling = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "billing-event"))

        for (baseKey in BillingEventBase.attributes.keys) {
            assertTrue(saasBilling.attributes.containsKey(baseKey),
                "SaaS billing event should contain base attribute '$baseKey'")
            assertTrue(dtcBilling.attributes.containsKey(baseKey),
                "DTC billing event should contain base attribute '$baseKey'")
        }
    }

    @Test
    fun `all variants of churn-event contain base attributes`() {
        val saasChurn = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "churn-event"))
        val dtcChurn = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "churn-event"))

        for (baseKey in ChurnEventBase.attributes.keys) {
            assertTrue(saasChurn.attributes.containsKey(baseKey),
                "SaaS churn event should contain base attribute '$baseKey'")
            assertTrue(dtcChurn.attributes.containsKey(baseKey),
                "DTC churn event should contain base attribute '$baseKey'")
        }
    }

    @Test
    fun `all variants of customer contain base attributes`() {
        val saasCustomer = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "customer"))
        val dtcCustomer = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "customer"))

        for (baseKey in CustomerBase.attributes.keys) {
            assertTrue(saasCustomer.attributes.containsKey(baseKey),
                "SaaS customer should contain base attribute '$baseKey'")
            assertTrue(dtcCustomer.attributes.containsKey(baseKey),
                "DTC customer should contain base attribute '$baseKey'")
        }
    }

    @Test
    fun `all variants of communication contain base attributes`() {
        val saasComm = requireNotNull(CoreModelRegistry.findModel("b2c-saas", "communication"))
        val dtcComm = requireNotNull(CoreModelRegistry.findModel("dtc-ecommerce", "communication"))

        for (baseKey in CommunicationBase.attributes.keys) {
            assertTrue(saasComm.attributes.containsKey(baseKey),
                "SaaS communication should contain base attribute '$baseKey'")
            assertTrue(dtcComm.attributes.containsKey(baseKey),
                "DTC communication should contain base attribute '$baseKey'")
        }
    }

    // ------ Manifest conversion tests ------

    @Test
    fun `toResolvedManifest produces valid manifest for b2c-saas`() {
        val modelSet = requireNotNull(CoreModelRegistry.findModelSet("b2c-saas")) {
            "b2c-saas model set should exist"
        }
        val manifest = CoreModelRegistry.toResolvedManifest(modelSet)

        assertEquals("b2c-saas", manifest.key)
        assertEquals(ManifestType.TEMPLATE, manifest.type)
        assertFalse(manifest.stale)
        assertTrue(manifest.entityTypes.isNotEmpty())
        assertTrue(manifest.relationships.isNotEmpty())
        assertTrue(manifest.fieldMappings.isEmpty())

        // Verify entity types match model count
        assertEquals(modelSet.models.size, manifest.entityTypes.size)

        // Verify customer entity type has correct attributes
        val customer = requireNotNull(manifest.entityTypes.find { it.key == "customer" }) {
            "customer entity type should exist in manifest"
        }
        assertEquals("Customer", customer.displayNameSingular)
        assertEquals("CUSTOMER", customer.semanticGroup)
        assertEquals(LifecycleDomain.UNCATEGORIZED, customer.lifecycleDomain)
        assertFalse(customer.readonly)
        assertTrue(customer.schema.containsKey("email"))
    }

    @Test
    fun `toResolvedManifest includes both model and additional relationships`() {
        val modelSet = requireNotNull(CoreModelRegistry.findModelSet("b2c-saas")) {
            "b2c-saas model set should exist"
        }
        val manifest = CoreModelRegistry.toResolvedManifest(modelSet)

        // Model-declared relationships (from SaasCustomerModel via CustomerBase)
        val supportTicketRel = manifest.relationships.find { it.key == "customer-support-tickets" }
        assertNotNull(supportTicketRel, "Should include model-declared relationship")

        // Additional relationships (from B2CSaasModels)
        val subscriptionRel = manifest.relationships.find { it.key == "customer-subscriptions" }
        assertNotNull(subscriptionRel, "Should include additional model set relationship")
    }

    /**
     * Regression: CoreModelRegistry previously only validated targetModelKey for additional
     * relationships, allowing a typo in sourceModelKey to pass validation silently. The fix
     * adds sourceModelKey validation. This test verifies that an invalid sourceModelKey is
     * caught at validation time.
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
        assertTrue(ex.message!!.contains("nonexistent-model"), "Error should name the invalid source key")
    }

    @Test
    fun `allResolvedManifests returns one manifest per model set`() {
        val manifests = CoreModelRegistry.allResolvedManifests()
        assertTrue(manifests.any { it.key == "b2c-saas" }, "Should contain b2c-saas manifest")
        assertTrue(manifests.any { it.key == "dtc-ecommerce" }, "Should contain dtc-ecommerce manifest")
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
