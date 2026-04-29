package riven.core.service.connotation

import org.springframework.stereotype.Service
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ScaleMappingType
import riven.core.models.catalog.SentimentScale
import riven.core.models.connotation.AnalysisTier
import riven.core.models.connotation.ConnotationStatus
import riven.core.models.connotation.SentimentAnalysisOutcome
import riven.core.models.connotation.SentimentAxis
import riven.core.models.connotation.SentimentFailureReason
import riven.core.models.connotation.SentimentLabel
import java.time.ZonedDateTime

/**
 * Pure deterministic Tier 1 sentiment mapper.
 *
 * Reads a single source attribute value and applies the manifest's [ConnotationSignals]
 * scale (LINEAR or THRESHOLD) to produce a unified `[-1.0, +1.0]` sentiment score and
 * coarse-grained [SentimentLabel]. Theme attributes are passed verbatim from caller-supplied
 * `themeValues` (the caller reads them off the entity payload).
 *
 * No DB, no logging, no clock injection — `analyzedAt` uses `ZonedDateTime.now()`. Tests
 * should not depend on the exact timestamp.
 */
@Service
class ConnotationTier1Mapper {

    fun analyze(
        signals: ConnotationSignals,
        sourceValue: Any?,
        themeValues: Map<String, String?>,
        activeVersion: String,
    ): SentimentAnalysisOutcome {
        if (sourceValue == null) {
            return SentimentAnalysisOutcome.Failure(
                SentimentFailureReason.MISSING_SOURCE_ATTRIBUTE,
                "sentimentAttribute '${signals.sentimentAttribute}' is null on the entity",
            )
        }
        val numeric = coerceToDouble(sourceValue) ?: return SentimentAnalysisOutcome.Failure(
            SentimentFailureReason.NON_NUMERIC_SOURCE_VALUE,
            "sentimentAttribute value '$sourceValue' is not numeric",
        )

        val score = when (signals.sentimentScale.mappingType) {
            ScaleMappingType.LINEAR -> linearMap(numeric, signals.sentimentScale)
            ScaleMappingType.THRESHOLD -> thresholdMap(numeric, signals.sentimentScale)
        }

        val themes = signals.themeAttributes.mapNotNull { themeValues[it] }

        val axis = SentimentAxis(
            sentiment = score,
            sentimentLabel = labelFor(score),
            themes = themes,
            analysisVersion = activeVersion,
            analysisModel = modelIdentifier(signals.sentimentScale.mappingType, activeVersion),
            analysisTier = AnalysisTier.TIER_1,
            status = ConnotationStatus.ANALYZED,
            analyzedAt = ZonedDateTime.now(),
        )
        return SentimentAnalysisOutcome.Success(axis)
    }

    private fun coerceToDouble(value: Any): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun linearMap(value: Double, scale: SentimentScale): Double {
        val clamped = value.coerceIn(scale.sourceMin, scale.sourceMax)
        val ratio = (clamped - scale.sourceMin) / (scale.sourceMax - scale.sourceMin)
        return scale.targetMin + ratio * (scale.targetMax - scale.targetMin)
    }

    private fun thresholdMap(value: Double, scale: SentimentScale): Double {
        val midpoint = (scale.sourceMin + scale.sourceMax) / 2.0
        return if (value < midpoint) scale.targetMin else scale.targetMax
    }

    private fun labelFor(score: Double): SentimentLabel = when {
        score <= -0.6 -> SentimentLabel.VERY_NEGATIVE
        score <= -0.2 -> SentimentLabel.NEGATIVE
        score < 0.2 -> SentimentLabel.NEUTRAL
        score < 0.6 -> SentimentLabel.POSITIVE
        else -> SentimentLabel.VERY_POSITIVE
    }

    private fun modelIdentifier(mappingType: ScaleMappingType, version: String): String =
        "connotation-tier1-${mappingType.name.lowercase()}-$version"
}
