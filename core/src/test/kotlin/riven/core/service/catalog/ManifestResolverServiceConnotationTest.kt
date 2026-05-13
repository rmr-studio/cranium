package cranium.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import cranium.core.enums.catalog.ManifestType
import cranium.core.models.catalog.ScaleMappingType
import cranium.core.models.catalog.ScannedManifest
import cranium.core.models.connotation.AnalysisTier
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Tests for the connotationSignals parsing + cross-validation step in
 * [ManifestResolverService.parseEntityType]. Mirrors the resolver's
 * relationship/field-mapping handling: warn + null on any cross-validation
 * failure, never throw.
 */
class ManifestResolverServiceConnotationTest {

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

    private fun buildIntegrationJson(entityTypes: List<Any>): JsonNode {
        return objectMapper.valueToTree(
            mapOf(
                "manifestVersion" to "1.0",
                "key" to "reviews-app",
                "name" to "Reviews App",
                "entityTypes" to entityTypes
            )
        )
    }

    private fun buildEntityType(
        key: String = "review",
        attributes: Map<String, Any> = mapOf(
            "rating" to mapOf("name" to "Rating", "type" to "NUMBER"),
            "tags" to mapOf("name" to "Tags", "type" to "TEXT")
        ),
        connotationSignals: Map<String, Any?>? = null,
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "key" to key,
            "displayName" to mapOf("singular" to key, "plural" to "${key}s"),
            "icon" to mapOf("type" to "BOX", "colour" to "NEUTRAL"),
            "semanticGroup" to "CUSTOM",
            "attributes" to attributes
        )
        if (connotationSignals != null) {
            map["connotationSignals"] = connotationSignals
        }
        return map
    }

    private fun linearSentimentScale(
        sourceMin: Double = 1.0,
        sourceMax: Double = 5.0,
        targetMin: Double = -1.0,
        targetMax: Double = 1.0,
    ): Map<String, Any> = mapOf(
        "sourceMin" to sourceMin,
        "sourceMax" to sourceMax,
        "targetMin" to targetMin,
        "targetMax" to targetMax,
        "mappingType" to "LINEAR"
    )

    private fun resolveSingle(entityTypeJson: Map<String, Any>) =
        service.resolveManifest(
            ScannedManifest(
                "reviews-app",
                ManifestType.INTEGRATION,
                buildIntegrationJson(listOf(entityTypeJson))
            )
        )

    /**
     * Capture every warn lambda the resolver invoked, evaluate them, and assert
     * at least one message contains the expected substring. This guards against
     * a connotation warn being silently dropped while an unrelated warn fires.
     */
    private fun assertWarnedWith(substring: String) {
        val captor = argumentCaptor<() -> Any>()
        verify(logger, atLeastOnce()).warn(captor.capture())
        val messages = captor.allValues.map { it.invoke().toString() }
        assertTrue(
            messages.any { it.contains(substring) },
            "Expected a warn message containing '$substring' but got: $messages"
        )
    }

    /** As [assertWarnedWith] but accepts any of several candidate substrings. */
    private fun assertWarnedWithAny(vararg substrings: String) {
        val captor = argumentCaptor<() -> Any>()
        verify(logger, atLeastOnce()).warn(captor.capture())
        val messages = captor.allValues.map { it.invoke().toString() }
        assertTrue(
            messages.any { msg -> substrings.any { msg.contains(it) } },
            "Expected a warn message containing any of ${substrings.toList()} but got: $messages"
        )
    }

    // ------ Cases ------

    @Test
    fun `parses LINEAR connotation signals when sentimentAttribute exists`() {
        val entityType = buildEntityType(
            connotationSignals = mapOf(
                "tier" to "DETERMINISTIC",
                "sentimentAttribute" to "rating",
                "sentimentScale" to linearSentimentScale(),
                "themeAttributes" to listOf("tags")
            )
        )

        val result = resolveSingle(entityType)

        assertFalse(result.stale)
        val resolvedType = result.entityTypes.single()
        val signals = resolvedType.connotationSignals
        assertNotNull(signals, "connotationSignals should be parsed when sentimentAttribute matches")
        assertEquals(AnalysisTier.DETERMINISTIC, signals!!.tier)
        assertEquals("rating", signals.sentimentAttribute)
        assertEquals(listOf("tags"), signals.themeAttributes)
        assertEquals(ScaleMappingType.LINEAR, signals.sentimentScale.mappingType)
        assertEquals(1.0, signals.sentimentScale.sourceMin)
        assertEquals(5.0, signals.sentimentScale.sourceMax)
        assertEquals(-1.0, signals.sentimentScale.targetMin)
        assertEquals(1.0, signals.sentimentScale.targetMax)
    }

    @Test
    fun `skips connotation signals when sentimentAttribute is unknown`() {
        val entityType = buildEntityType(
            connotationSignals = mapOf(
                "tier" to "DETERMINISTIC",
                "sentimentAttribute" to "nonexistent_attribute",
                "sentimentScale" to linearSentimentScale(),
                "themeAttributes" to emptyList<String>()
            )
        )

        val result = resolveSingle(entityType)

        assertFalse(result.stale)
        assertNull(result.entityTypes.single().connotationSignals)
        assertWarnedWith("sentimentAttribute")
    }

    @Test
    fun `skips connotation signals when themeAttributes contains unknown key`() {
        val entityType = buildEntityType(
            connotationSignals = mapOf(
                "tier" to "DETERMINISTIC",
                "sentimentAttribute" to "rating",
                "sentimentScale" to linearSentimentScale(),
                "themeAttributes" to listOf("tags", "missing_theme")
            )
        )

        val result = resolveSingle(entityType)

        assertFalse(result.stale)
        assertNull(result.entityTypes.single().connotationSignals)
        assertWarnedWith("themeAttributes")
    }

    @Test
    fun `skips connotation signals when sourceMin is greater than or equal to sourceMax`() {
        val entityType = buildEntityType(
            connotationSignals = mapOf(
                "tier" to "DETERMINISTIC",
                "sentimentAttribute" to "rating",
                "sentimentScale" to linearSentimentScale(sourceMin = 5.0, sourceMax = 1.0),
                "themeAttributes" to emptyList<String>()
            )
        )

        val result = resolveSingle(entityType)

        assertFalse(result.stale)
        assertNull(result.entityTypes.single().connotationSignals)
        assertWarnedWithAny("sourceMin", "sourceMax")
    }

    @Test
    fun `skips connotation signals when targetMin is greater than or equal to targetMax`() {
        val entityType = buildEntityType(
            connotationSignals = mapOf(
                "tier" to "DETERMINISTIC",
                "sentimentAttribute" to "rating",
                "sentimentScale" to linearSentimentScale(targetMin = 1.0, targetMax = -1.0),
                "themeAttributes" to emptyList<String>()
            )
        )

        val result = resolveSingle(entityType)

        assertFalse(result.stale)
        assertNull(result.entityTypes.single().connotationSignals)
        assertWarnedWithAny("targetMin", "targetMax")
    }

    @Test
    fun `entity types without connotationSignals parse cleanly`() {
        val entityType = buildEntityType(connotationSignals = null)

        val result = resolveSingle(entityType)

        assertFalse(result.stale)
        assertNull(result.entityTypes.single().connotationSignals)
    }
}
