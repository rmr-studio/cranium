package riven.core.service.catalog

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
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
        objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
        logger = mock()
        service = ManifestResolverService(objectMapper, logger)
    }

    // ------ Helpers ------

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

    private fun buildShorthandRelationship(
        key: String,
        sourceKey: String,
        targetKey: String,
        cardinality: String = "ONE_TO_MANY",
        name: String = "Related",
        protectedVal: Boolean? = null
    ): Map<String, Any?> {
        val rel = mutableMapOf<String, Any?>(
            "key" to key,
            "sourceEntityTypeKey" to sourceKey,
            "name" to name,
            "cardinality" to cardinality,
            "targetEntityTypeKey" to targetKey
        )
        protectedVal?.let { rel["protected"] = it }
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

    // ------ Relationship Normalization ------

    @Nested
    inner class RelationshipNormalization {

        @Test
        fun `shorthand converted to full format`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(
                    buildInlineEntityType("hubspot-contact", "HubSpot Contact", readonly = true),
                    buildInlineEntityType("hubspot-deal", "HubSpot Deal", readonly = true)
                ),
                relationships = listOf(
                    buildShorthandRelationship("contact-deals", "hubspot-contact", "hubspot-deal", "ONE_TO_MANY")
                )
            )
            val scanned = ScannedManifest("hubspot", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned)

            assertFalse(result.stale)
            assertEquals(1, result.relationships.size)
            val rel = result.relationships[0]
            assertEquals(EntityRelationshipCardinality.ONE_TO_MANY, rel.cardinalityDefault)
            assertEquals(1, rel.targetRules.size)
            assertEquals("hubspot-deal", rel.targetRules[0].targetEntityTypeKey)
        }

        @Test
        fun `full format passed through`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(
                    buildInlineEntityType("hubspot-deal", "HubSpot Deal", readonly = true),
                    buildInlineEntityType("hubspot-contact", "HubSpot Contact", readonly = true)
                ),
                relationships = listOf(
                    buildFullFormatRelationship(
                        "deal-contact", "hubspot-deal", "Contact",
                        targetRules = listOf(mapOf("targetEntityTypeKey" to "hubspot-contact"))
                    )
                )
            )
            val scanned = ScannedManifest("hubspot", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned)

            assertFalse(result.stale)
            assertEquals(1, result.relationships.size)
            assertEquals("hubspot-contact", result.relationships[0].targetRules[0].targetEntityTypeKey)
        }

        @Test
        fun `both shorthand and full format returns stale manifest`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(
                    buildInlineEntityType("a", "A", readonly = true),
                    buildInlineEntityType("b", "B", readonly = true)
                ),
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
            val scanned = ScannedManifest("bad", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned)

            assertTrue(result.stale)
        }
    }

    // ------ Relationship Validation ------

    @Nested
    inner class RelationshipValidation {

        @Test
        fun `valid source and target keys pass`() {
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

            val result = service.resolveManifest(scanned)

            assertFalse(result.stale)
        }

        @Test
        fun `missing source key returns stale`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(buildInlineEntityType("hubspot-deal", "HubSpot Deal", readonly = true)),
                relationships = listOf(
                    buildShorthandRelationship("bad-rel", "nonexistent", "hubspot-deal")
                )
            )
            val scanned = ScannedManifest("bad", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned)

            assertTrue(result.stale)
        }

        @Test
        fun `duplicate relationship keys detected`() {
            val integrationJson = buildIntegrationJson(
                entityTypes = listOf(
                    buildInlineEntityType("hubspot-contact", "HubSpot Contact", readonly = true),
                    buildInlineEntityType("hubspot-deal", "HubSpot Deal", readonly = true)
                ),
                relationships = listOf(
                    buildShorthandRelationship("same-key", "hubspot-contact", "hubspot-deal"),
                    buildShorthandRelationship("same-key", "hubspot-deal", "hubspot-contact")
                )
            )
            val scanned = ScannedManifest("dup", ManifestType.INTEGRATION, integrationJson)

            val result = service.resolveManifest(scanned)

            assertTrue(result.stale)
        }
    }

    // ------ Protected Defaults ------

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

            val result = service.resolveManifest(scanned)

            assertTrue(result.relationships[0].`protected`)
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

            val result = service.resolveManifest(scanned)

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

            val result = service.resolveManifest(scanned)

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
}
