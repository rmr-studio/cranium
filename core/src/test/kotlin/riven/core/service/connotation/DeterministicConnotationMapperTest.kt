package cranium.core.service.connotation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import cranium.core.enums.connotation.ConnotationStatus
import cranium.core.models.catalog.ConnotationSignals
import cranium.core.models.catalog.ScaleMappingType
import cranium.core.models.catalog.SentimentScale
import cranium.core.models.connotation.AnalysisTier
import cranium.core.models.connotation.SentimentAnalysisOutcome
import cranium.core.models.connotation.SentimentFailureReason
import cranium.core.models.connotation.SentimentLabel

class DeterministicConnotationMapperTest {

    private val mapper = DeterministicConnotationMapper()
    private val activeVersion = "v1"

    private fun signals(
        attribute: String = "satisfaction_score",
        sourceMin: Double = 1.0, sourceMax: Double = 5.0,
        targetMin: Double = -1.0, targetMax: Double = 1.0,
        mappingType: ScaleMappingType = ScaleMappingType.LINEAR,
        themes: List<String> = emptyList(),
    ) = ConnotationSignals(
        tier = AnalysisTier.DETERMINISTIC,
        sentimentAttribute = attribute,
        sentimentScale = SentimentScale(sourceMin, sourceMax, targetMin, targetMax, mappingType),
        themeAttributes = themes,
    )

    @Test
    fun `LINEAR maps midpoint to zero`() {
        val outcome = mapper.analyze(
            signals = signals(),
            sourceValue = 3.0,
            themeValues = emptyMap(),
            activeVersion = activeVersion,
        )

        // Assert Success
        assertTrue(outcome is SentimentAnalysisOutcome.Success)
        (outcome as SentimentAnalysisOutcome.Success).metadata.also { metadata ->
             assertEquals(0.0, metadata.sentiment!!, 1e-9)
             assertEquals(SentimentLabel.NEUTRAL, metadata.sentimentLabel)
             assertEquals(activeVersion, metadata.analysisVersion)
             assertEquals(AnalysisTier.DETERMINISTIC, metadata.analysisTier)
             assertEquals("deterministic-connotation-linear-v1", metadata.analysisModel)
             assertEquals(ConnotationStatus.ANALYZED, metadata.status)
             assertNotNull(metadata.analyzedAt)
        }

    }

    @Test
    fun `LINEAR maps source max to plus one`() {
        val outcome = mapper.analyze(signals(), 5.0, emptyMap(), activeVersion)
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(1.0, metadata.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_POSITIVE, metadata.sentimentLabel)
    }

    @Test
    fun `LINEAR maps source min to minus one`() {
        val outcome = mapper.analyze(signals(), 1.0, emptyMap(), activeVersion)
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(-1.0, metadata.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_NEGATIVE, metadata.sentimentLabel)
    }

    @Test
    fun `LINEAR clamps below sourceMin to targetMin`() {
        val outcome = mapper.analyze(signals(), 0.0, emptyMap(), activeVersion)
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(-1.0, metadata.sentiment!!, 1e-9)
    }

    @Test
    fun `LINEAR clamps above sourceMax to targetMax`() {
        val outcome = mapper.analyze(signals(), 99.0, emptyMap(), activeVersion)
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(1.0, metadata.sentiment!!, 1e-9)
    }

    @Test
    fun `THRESHOLD maps below midpoint to targetMin`() {
        val outcome = mapper.analyze(
            signals(mappingType = ScaleMappingType.THRESHOLD),
            sourceValue = 2.0, themeValues = emptyMap(), activeVersion = activeVersion,
        )
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(-1.0, metadata.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_NEGATIVE, metadata.sentimentLabel)
    }

    @Test
    fun `THRESHOLD maps at-or-above midpoint to targetMax`() {
        val outcome = mapper.analyze(
            signals(mappingType = ScaleMappingType.THRESHOLD),
            sourceValue = 4.0, themeValues = emptyMap(), activeVersion = activeVersion,
        )
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(1.0, metadata.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_POSITIVE, metadata.sentimentLabel)
    }

    @Test
    fun `null source value returns MISSING_SOURCE_ATTRIBUTE failure`() {
        val outcome = mapper.analyze(signals(), sourceValue = null, emptyMap(), activeVersion)
        val failure = outcome as SentimentAnalysisOutcome.Failure
        assertEquals(SentimentFailureReason.MISSING_SOURCE_ATTRIBUTE, failure.reason)
    }

    @Test
    fun `non-numeric source value returns NON_NUMERIC_SOURCE_VALUE failure`() {
        val outcome = mapper.analyze(signals(), sourceValue = "not-a-number", emptyMap(), activeVersion)
        val failure = outcome as SentimentAnalysisOutcome.Failure
        assertEquals(SentimentFailureReason.NON_NUMERIC_SOURCE_VALUE, failure.reason)
    }

    @Test
    fun `themeAttributes are copied verbatim from themeValues`() {
        val outcome = mapper.analyze(
            signals(themes = listOf("tags", "category")),
            sourceValue = 4.0,
            themeValues = mapOf("tags" to "billing,support", "category" to "complaint"),
            activeVersion = activeVersion,
        )
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(listOf("billing,support", "complaint"), metadata.themes)
    }

    @Test
    fun `themeAttributes with null values are skipped`() {
        val outcome = mapper.analyze(
            signals(themes = listOf("tags", "category")),
            sourceValue = 4.0,
            themeValues = mapOf("tags" to "billing", "category" to null),
            activeVersion = activeVersion,
        )
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(listOf("billing"), metadata.themes)
    }

    @Test
    fun `numeric strings are parsed as source value`() {
        val outcome = mapper.analyze(signals(), sourceValue = "3.0", emptyMap(), activeVersion)
        val metadata = (outcome as SentimentAnalysisOutcome.Success).metadata
        assertEquals(0.0, metadata.sentiment!!, 1e-9)
    }
}
