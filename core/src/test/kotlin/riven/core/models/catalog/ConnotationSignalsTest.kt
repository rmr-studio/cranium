package riven.core.models.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class ConnotationSignalsTest {

    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    @Test
    fun `LINEAR sentiment scale round-trips through Jackson`() {
        val signals = ConnotationSignals(
            tier = "ACCURATE",
            sentimentAttribute = "rating",
            sentimentScale = SentimentScale(
                sourceMin = 1.0,
                sourceMax = 5.0,
                targetMin = -1.0,
                targetMax = 1.0,
                mappingType = ScaleMappingType.LINEAR,
            ),
            themeAttributes = listOf("review_text", "summary"),
        )

        val json = mapper.writeValueAsString(signals)
        val parsed = mapper.readValue(json, ConnotationSignals::class.java)

        assertEquals(signals, parsed)
        assertEquals(ScaleMappingType.LINEAR, parsed.sentimentScale.mappingType)
        assertEquals("rating", parsed.sentimentAttribute)
        assertEquals(listOf("review_text", "summary"), parsed.themeAttributes)
    }

    @Test
    fun `THRESHOLD mapping type deserializes from JSON`() {
        val json = """
            {
                "tier": "FAST",
                "sentimentAttribute": "score",
                "sentimentScale": {
                    "sourceMin": 0.0,
                    "sourceMax": 100.0,
                    "targetMin": -1.0,
                    "targetMax": 1.0,
                    "mappingType": "THRESHOLD"
                },
                "themeAttributes": []
            }
        """.trimIndent()

        val parsed = mapper.readValue(json, ConnotationSignals::class.java)

        assertEquals("FAST", parsed.tier)
        assertEquals("score", parsed.sentimentAttribute)
        assertEquals(ScaleMappingType.THRESHOLD, parsed.sentimentScale.mappingType)
        assertEquals(0.0, parsed.sentimentScale.sourceMin)
        assertEquals(100.0, parsed.sentimentScale.sourceMax)
        assertEquals(emptyList<String>(), parsed.themeAttributes)
    }
}
