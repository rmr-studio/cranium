package riven.core.service.connotation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ScaleMappingType
import riven.core.models.catalog.SentimentScale
import riven.core.models.connotation.AnalysisTier
import riven.core.models.connotation.ConnotationStatus
import riven.core.models.connotation.SentimentAnalysisOutcome
import riven.core.models.connotation.SentimentFailureReason
import riven.core.models.connotation.SentimentLabel

class ConnotationTier1MapperTest {

    private val mapper = ConnotationTier1Mapper()
    private val activeVersion = "v1"

    private fun signals(
        attribute: String = "satisfaction_score",
        sourceMin: Double = 1.0, sourceMax: Double = 5.0,
        targetMin: Double = -1.0, targetMax: Double = 1.0,
        mappingType: ScaleMappingType = ScaleMappingType.LINEAR,
        themes: List<String> = emptyList(),
    ) = ConnotationSignals(
        tier = AnalysisTier.TIER_1,
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

        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(0.0, axis.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.NEUTRAL, axis.sentimentLabel)
        assertEquals(activeVersion, axis.analysisVersion)
        assertEquals(AnalysisTier.TIER_1, axis.analysisTier)
        assertEquals("connotation-tier1-linear-v1", axis.analysisModel)
        assertEquals(ConnotationStatus.ANALYZED, axis.status)
        assertNotNull(axis.analyzedAt)
    }

    @Test
    fun `LINEAR maps source max to plus one`() {
        val outcome = mapper.analyze(signals(), 5.0, emptyMap(), activeVersion)
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(1.0, axis.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_POSITIVE, axis.sentimentLabel)
    }

    @Test
    fun `LINEAR maps source min to minus one`() {
        val outcome = mapper.analyze(signals(), 1.0, emptyMap(), activeVersion)
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(-1.0, axis.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_NEGATIVE, axis.sentimentLabel)
    }

    @Test
    fun `LINEAR clamps below sourceMin to targetMin`() {
        val outcome = mapper.analyze(signals(), 0.0, emptyMap(), activeVersion)
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(-1.0, axis.sentiment!!, 1e-9)
    }

    @Test
    fun `LINEAR clamps above sourceMax to targetMax`() {
        val outcome = mapper.analyze(signals(), 99.0, emptyMap(), activeVersion)
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(1.0, axis.sentiment!!, 1e-9)
    }

    @Test
    fun `THRESHOLD maps below midpoint to targetMin`() {
        val outcome = mapper.analyze(
            signals(mappingType = ScaleMappingType.THRESHOLD),
            sourceValue = 2.0, themeValues = emptyMap(), activeVersion = activeVersion,
        )
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(-1.0, axis.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_NEGATIVE, axis.sentimentLabel)
    }

    @Test
    fun `THRESHOLD maps at-or-above midpoint to targetMax`() {
        val outcome = mapper.analyze(
            signals(mappingType = ScaleMappingType.THRESHOLD),
            sourceValue = 4.0, themeValues = emptyMap(), activeVersion = activeVersion,
        )
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(1.0, axis.sentiment!!, 1e-9)
        assertEquals(SentimentLabel.VERY_POSITIVE, axis.sentimentLabel)
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
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(listOf("billing,support", "complaint"), axis.themes)
    }

    @Test
    fun `themeAttributes with null values are skipped`() {
        val outcome = mapper.analyze(
            signals(themes = listOf("tags", "category")),
            sourceValue = 4.0,
            themeValues = mapOf("tags" to "billing", "category" to null),
            activeVersion = activeVersion,
        )
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(listOf("billing"), axis.themes)
    }

    @Test
    fun `numeric strings are parsed as source value`() {
        val outcome = mapper.analyze(signals(), sourceValue = "3.0", emptyMap(), activeVersion)
        val axis = (outcome as SentimentAnalysisOutcome.Success).axis
        assertEquals(0.0, axis.sentiment!!, 1e-9)
    }
}
