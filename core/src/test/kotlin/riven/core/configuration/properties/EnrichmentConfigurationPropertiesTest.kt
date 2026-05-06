package riven.core.configuration.properties

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

/**
 * CONF-01 contract: verifies that [EnrichmentConfigurationProperties] exposes the two new
 * Phase 2 fields with their correct literal defaults and that Spring relaxed binding works
 * for the kebab-case external property form.
 */
class EnrichmentConfigurationPropertiesTest {

    /**
     * CONF-01 — knowledgeBacklinkCap default: verifies the literal default is 3,
     * matching the assembler cap specified in the feature design.
     */
    @Test
    fun `CONF-01 - knowledgeBacklinkCap default is 3`() {
        val props = EnrichmentConfigurationProperties()
        assertEquals(3, props.knowledgeBacklinkCap, "knowledgeBacklinkCap default must be exactly 3 (CONF-01).")
    }

    /**
     * CONF-01 — viewPayloadWarnBytes default: verifies the literal default is 1,048,576 (1 MiB),
     * NOT 1,000,000 (1 MB). This is the Phase 3 size-guard log threshold.
     */
    @Test
    fun `CONF-01 - viewPayloadWarnBytes default is 1 MiB (1_048_576L)`() {
        val props = EnrichmentConfigurationProperties()
        assertEquals(1_048_576L, props.viewPayloadWarnBytes, "viewPayloadWarnBytes default must be exactly 1,048,576 (1 MiB, not 1 MB).")
    }

    /**
     * CONF-01 — Spring relaxed binding: proves that the two new fields bind correctly from
     * their kebab-case external form (riven.enrichment.knowledge-backlink-cap and
     * riven.enrichment.view-payload-warn-bytes) as Spring Boot @ConfigurationProperties
     * consumers would supply them in application.yml.
     */
    @Test
    fun `CONF-01 - kebab-case binding works for both new fields`() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "riven.enrichment.knowledge-backlink-cap" to "5",
                "riven.enrichment.view-payload-warn-bytes" to "2097152",
            )
        )
        val binder = Binder(source)
        val bound = binder.bind("riven.enrichment", EnrichmentConfigurationProperties::class.java).get()

        assertEquals(5, bound.knowledgeBacklinkCap, "Bound knowledgeBacklinkCap must be 5.")
        assertEquals(2_097_152L, bound.viewPayloadWarnBytes, "Bound viewPayloadWarnBytes must be 2,097,152.")
    }
}
