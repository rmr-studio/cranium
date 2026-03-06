package riven.core.service.catalog

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import riven.core.enums.catalog.ManifestType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.models.catalog.ScannedManifest

class ManifestResolverServiceTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var logger: KLogger
    private lateinit var service: ManifestResolverService

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        logger = mock()
        service = ManifestResolverService(objectMapper, logger)
    }

    // ------ Helpers ------

    private fun buildModelJson(
        key: String = "customer",
        name: String = "Customer",
        attributes: Map<String, Any> = mapOf(
            "name" to mapOf("key" to "TEXT", "type" to "string"),
            "email" to mapOf("key" to "EMAIL", "type" to "string")
        ),
        semanticGroup: String = "CUSTOMER",
        identifierKey: String? = "email",
        semantics: Map<String, Any>? = mapOf(
            "definition" to "A customer entity",
            "tags" to listOf("crm", "sales")
        )
    ): JsonNode {
        val map = mutableMapOf<String, Any?>(
            "manifestVersion" to "1.0",
            "key" to key,
            "name" to name,
            "displayName" to mapOf("singular" to name, "plural" to "${name}s"),
            "icon" to mapOf("type" to "USERS", "colour" to "BLUE"),
            "semanticGroup" to semanticGroup,
            "attributes" to attributes
        )
        identifierKey?.let { map["identifierKey"] = it }
        semantics?.let { map["semantics"] = it }
        return objectMapper.valueToTree(map)
    }

    private fun buildTemplateJson(
        key: String = "saas-starter",
        name: String = "SaaS Starter",
        entityTypes: List<Any>,
        relationships: List<Any> = emptyList()
    ): JsonNode {
        val map = mutableMapOf<String, Any>(
            "manifestVersion" to "1.0",
            "key" to key,
            "name" to name,
            "entityTypes" to entityTypes
        )
        if (relationships.isNotEmpty()) {
            map["relationships"] = relationships
        }
        return objectMapper.valueToTree(map)
    }

    private fun buildIntegrationJson(
        key: String = "hubspot",
        name: String = "HubSpot",
        entityTypes: List<Any>,
        relationships: List<Any> = emptyList(),
        fieldMappings: List<Any> = emptyList()
    ): JsonNode {
        val map = mutableMapOf<String, Any>(
            "manifestVersion" to "1.0",
            "key" to key,
            "name" to name,
            "entityTypes" to entityTypes
        )
        if (relationships.isNotEmpty()) map["relationships"] = relationships
        if (fieldMappings.isNotEmpty()) map["fieldMappings"] = fieldMappings
        return objectMapper.valueToTree(map)
    }

    private fun buildInlineEntityType(
        key: String,
        name: String,
        attributes: Map<String, Any> = mapOf("title" to mapOf("key" to "TEXT", "type" to "string")),
        semanticGroup: String = "CUSTOM",
        readonly: Boolean = false
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "key" to key,
            "name" to name,
            "displayName" to mapOf("singular" to name, "plural" to "${name}s"),
            "icon" to mapOf("type" to "BOX", "colour" to "NEUTRAL"),
            "semanticGroup" to semanticGroup,
            "attributes" to attributes
        )
        if (readonly) map["readonly"] = true
        return map
    }

    private fun buildRefEntry(modelKey: String, extend: Map<String, Any>? = null): Map<String, Any> {
        val entry = mutableMapOf<String, Any>("\$ref" to "models/$modelKey")
        extend?.let { entry["extend"] = it }
        return entry
    }

    private fun buildShorthandRelationship(
        key: String,
        sourceKey: String,
        targetKey: String,
        cardinality: String = "ONE_TO_MANY",
        name: String = "Related",
        protectedVal: Boolean? = null,
        semantics: Map<String, Any>? = null
    ): Map<String, Any?> {
        val rel = mutableMapOf<String, Any?>(
            "key" to key,
            "sourceEntityTypeKey" to sourceKey,
            "name" to name,
            "cardinality" to cardinality,
            "targetEntityTypeKey" to targetKey
        )
        protectedVal?.let { rel["protected"] = it }
        semantics?.let { rel["semantics"] = it }
        return rel
    }

    private fun buildFullFormatRelationship(
        key: String,
        sourceKey: String,
        name: String = "Related",
        targetRules: List<Map<String, Any>>,
        protectedVal: Boolean? = null
    ): Map<String, Any?> {
        val rel = mutableMapOf<String, Any?>(
            "key" to key,
            "sourceEntityTypeKey" to sourceKey,
            "name" to name,
            "targetRules" to targetRules
        )
        protectedVal?.let { rel["protected"] = it }
        return rel
    }

    // ------ TEST-01: $ref Resolution ------

    @Nested
    inner class RefResolution {

        @Test
        fun `resolves ref pointing to existing model`() {
            val modelJson = buildModelJson()
            val modelIndex = mapOf("customer" to modelJson)
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildRefEntry("customer"))
            )
            val scanned = ScannedManifest("saas-starter", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, modelIndex)

            assertFalse(result.stale)
            assertEquals(1, result.entityTypes.size)
            assertEquals("customer", result.entityTypes[0].key)
            assertEquals("Customer", result.entityTypes[0].displayNameSingular)
        }

        @Test
        fun `missing model ref marks manifest stale`() {
            val modelIndex = emptyMap<String, JsonNode>()
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildRefEntry("nonexistent"))
            )
            val scanned = ScannedManifest("broken-template", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, modelIndex)

            assertTrue(result.stale)
            assertTrue(result.entityTypes.isEmpty())
        }

        @Test
        fun `inline entity type parsed without ref`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildInlineEntityType("deal", "Deal"))
            )
            val scanned = ScannedManifest("custom-template", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertFalse(result.stale)
            assertEquals(1, result.entityTypes.size)
            assertEquals("deal", result.entityTypes[0].key)
        }
    }

    // ------ TEST-02: Extend Merge ------

    @Nested
    inner class ExtendMerge {

        @Test
        fun `extend adds new attributes`() {
            val modelJson = buildModelJson()
            val modelIndex = mapOf("customer" to modelJson)
            val extend = mapOf(
                "attributes" to mapOf(
                    "subscription-tier" to mapOf("key" to "SELECT", "type" to "string")
                )
            )
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildRefEntry("customer", extend))
            )
            val scanned = ScannedManifest("saas", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, modelIndex)

            val entityType = result.entityTypes[0]
            // Should have original attributes plus the new one
            assertTrue(entityType.schema.containsKey("name"))
            assertTrue(entityType.schema.containsKey("email"))
            assertTrue(entityType.schema.containsKey("subscription-tier"))
        }

        @Test
        fun `extend conflicting attribute key preserves base`() {
            val modelJson = buildModelJson()
            val modelIndex = mapOf("customer" to modelJson)
            val extend = mapOf(
                "attributes" to mapOf(
                    "email" to mapOf("key" to "TEXT", "type" to "string", "label" to "Override Email")
                )
            )
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildRefEntry("customer", extend))
            )
            val scanned = ScannedManifest("saas", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, modelIndex)

            val entityType = result.entityTypes[0]
            // Base attribute should be preserved -- key should still be EMAIL, not TEXT
            @Suppress("UNCHECKED_CAST")
            val emailAttr = entityType.schema["email"] as Map<String, Any>
            assertEquals("EMAIL", emailAttr["key"])
        }

        @Test
        fun `extend overrides scalar fields`() {
            val modelJson = buildModelJson(semanticGroup = "CUSTOMER")
            val modelIndex = mapOf("customer" to modelJson)
            val extend = mapOf(
                "description" to "Extended description",
                "semanticGroup" to "CUSTOM"
            )
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildRefEntry("customer", extend))
            )
            val scanned = ScannedManifest("saas", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, modelIndex)

            assertEquals("CUSTOM", result.entityTypes[0].semanticGroup)
        }

        @Test
        fun `extend appends semantic tags`() {
            val modelJson = buildModelJson(
                semantics = mapOf("definition" to "A customer", "tags" to listOf("crm", "sales"))
            )
            val modelIndex = mapOf("customer" to modelJson)
            val extend = mapOf(
                "semantics" to mapOf("tags" to listOf("saas", "subscription"))
            )
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildRefEntry("customer", extend))
            )
            val scanned = ScannedManifest("saas", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, modelIndex)

            val tags = result.entityTypes[0].semantics?.tags ?: emptyList()
            assertTrue(tags.contains("crm"))
            assertTrue(tags.contains("sales"))
            assertTrue(tags.contains("saas"))
            assertTrue(tags.contains("subscription"))
        }
    }

    // ------ TEST-03: Relationship Normalization ------

    @Nested
    inner class RelationshipNormalization {

        @Test
        fun `shorthand converted to full format`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(
                    buildInlineEntityType("customer", "Customer"),
                    buildInlineEntityType("deal", "Deal")
                ),
                relationships = listOf(
                    buildShorthandRelationship("customer-deals", "customer", "deal", "ONE_TO_MANY")
                )
            )
            val scanned = ScannedManifest("test", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertFalse(result.stale)
            assertEquals(1, result.relationships.size)
            val rel = result.relationships[0]
            assertEquals(EntityRelationshipCardinality.ONE_TO_MANY, rel.cardinalityDefault)
            assertEquals(1, rel.targetRules.size)
            assertEquals("deal", rel.targetRules[0].targetEntityTypeKey)
        }

        @Test
        fun `full format passed through`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(
                    buildInlineEntityType("deal", "Deal"),
                    buildInlineEntityType("customer", "Customer")
                ),
                relationships = listOf(
                    buildFullFormatRelationship(
                        "deal-customer", "deal", "Customer",
                        targetRules = listOf(mapOf("targetEntityTypeKey" to "customer"))
                    )
                )
            )
            val scanned = ScannedManifest("test", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertFalse(result.stale)
            assertEquals(1, result.relationships.size)
            assertEquals("customer", result.relationships[0].targetRules[0].targetEntityTypeKey)
        }

        @Test
        fun `both shorthand and full format returns stale manifest`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildInlineEntityType("a", "A"), buildInlineEntityType("b", "B")),
                relationships = listOf(
                    mapOf(
                        "key" to "mixed",
                        "sourceEntityTypeKey" to "a",
                        "name" to "Mixed",
                        "cardinality" to "ONE_TO_MANY",
                        "targetEntityTypeKey" to "b",
                        "targetRules" to listOf(mapOf("targetEntityTypeKey" to "b"))
                    )
                )
            )
            val scanned = ScannedManifest("bad", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertTrue(result.stale)
        }
    }

    // ------ TEST-04: Relationship Validation ------

    @Nested
    inner class RelationshipValidation {

        @Test
        fun `valid source and target keys pass`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(
                    buildInlineEntityType("customer", "Customer"),
                    buildInlineEntityType("deal", "Deal")
                ),
                relationships = listOf(
                    buildShorthandRelationship("customer-deals", "customer", "deal")
                )
            )
            val scanned = ScannedManifest("valid", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertFalse(result.stale)
        }

        @Test
        fun `missing source key returns stale`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(buildInlineEntityType("deal", "Deal")),
                relationships = listOf(
                    buildShorthandRelationship("bad-rel", "nonexistent", "deal")
                )
            )
            val scanned = ScannedManifest("bad", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertTrue(result.stale)
        }

        @Test
        fun `duplicate relationship keys detected`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(
                    buildInlineEntityType("customer", "Customer"),
                    buildInlineEntityType("deal", "Deal")
                ),
                relationships = listOf(
                    buildShorthandRelationship("same-key", "customer", "deal"),
                    buildShorthandRelationship("same-key", "deal", "customer")
                )
            )
            val scanned = ScannedManifest("dup", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertTrue(result.stale)
        }
    }

    // ------ LOAD-07: Protected Defaults ------

    @Nested
    inner class ProtectedDefaults {

        @Test
        fun `INTEGRATION defaults protected to true`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(
                    buildInlineEntityType("hubspot-contact", "HubSpot Contact", readonly = true),
                    buildInlineEntityType("hubspot-deal", "HubSpot Deal", readonly = true)
                ),
                relationships = listOf(
                    buildShorthandRelationship("contact-deals", "hubspot-contact", "hubspot-deal")
                )
            )
            val scanned = ScannedManifest("hubspot", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertTrue(result.relationships[0].`protected`)
        }

        @Test
        fun `TEMPLATE defaults protected to false`() {
            val templateJson = buildTemplateJson(
                entityTypes = listOf(
                    buildInlineEntityType("customer", "Customer"),
                    buildInlineEntityType("deal", "Deal")
                ),
                relationships = listOf(
                    buildShorthandRelationship("customer-deals", "customer", "deal")
                )
            )
            val scanned = ScannedManifest("test", ManifestType.TEMPLATE, templateJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertFalse(result.relationships[0].`protected`)
        }
    }

    // ------ Field Mapping Validation ------

    @Nested
    inner class FieldMappingValidation {

        @Test
        fun `valid mapping keys included`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(
                    buildInlineEntityType(
                        "hubspot-contact", "HubSpot Contact",
                        attributes = mapOf(
                            "email" to mapOf("key" to "EMAIL", "type" to "string"),
                            "name" to mapOf("key" to "TEXT", "type" to "string")
                        ),
                        readonly = true
                    )
                ),
                fieldMappings = listOf(
                    mapOf(
                        "entityTypeKey" to "hubspot-contact",
                        "mappings" to mapOf(
                            "email" to mapOf("source" to "properties.email"),
                            "name" to mapOf("source" to "properties.name")
                        )
                    )
                )
            )
            val scanned = ScannedManifest("hubspot", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertEquals(1, result.fieldMappings.size)
            assertEquals(2, result.fieldMappings[0].mappings.size)
        }

        @Test
        fun `invalid mapping key skipped with WARN`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(
                    buildInlineEntityType(
                        "hubspot-contact", "HubSpot Contact",
                        attributes = mapOf(
                            "email" to mapOf("key" to "EMAIL", "type" to "string")
                        ),
                        readonly = true
                    )
                ),
                fieldMappings = listOf(
                    mapOf(
                        "entityTypeKey" to "hubspot-contact",
                        "mappings" to mapOf(
                            "email" to mapOf("source" to "properties.email"),
                            "nonexistent" to mapOf("source" to "properties.foo")
                        )
                    )
                )
            )
            val scanned = ScannedManifest("hubspot", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned, emptyMap())

            // Should still have the field mapping entry, but only valid keys
            assertFalse(result.stale) // Field mapping failures do NOT cause stale
            assertEquals(1, result.fieldMappings.size)
            assertEquals(1, result.fieldMappings[0].mappings.size)
            assertTrue(result.fieldMappings[0].mappings.containsKey("email"))
            assertFalse(result.fieldMappings[0].mappings.containsKey("nonexistent"))

            // Verify warn was logged for the invalid mapping key
            verify(logger).warn(any<() -> Any>())
        }
    }

    // ------ MODEL manifest ------

    @Nested
    inner class ModelManifest {

        @Test
        fun `model manifest resolves single entity type from root`() {
            val modelJson = buildModelJson()
            val scanned = ScannedManifest("customer", ManifestType.MODEL, modelJson)

            val result = service.resolveManifest(scanned, emptyMap())

            assertFalse(result.stale)
            assertEquals(1, result.entityTypes.size)
            assertEquals("customer", result.entityTypes[0].key)
            assertEquals("Customer", result.entityTypes[0].displayNameSingular)
            assertEquals("Customers", result.entityTypes[0].displayNamePlural)
            assertEquals("USERS", result.entityTypes[0].iconType)
            assertEquals("BLUE", result.entityTypes[0].iconColour)
            assertEquals("CUSTOMER", result.entityTypes[0].semanticGroup)
            assertEquals("email", result.entityTypes[0].identifierKey)
            assertFalse(result.entityTypes[0].readonly)
        }
    }
}
