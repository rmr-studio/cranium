package cranium.core.service.connotation

import org.springframework.stereotype.Service
import cranium.core.enums.connotation.ConnotationStatus
import cranium.core.models.catalog.ConnotationSignals
import cranium.core.models.catalog.ScaleMappingType
import cranium.core.models.catalog.SentimentScale
import cranium.core.models.common.json.JsonValue
import cranium.core.models.connotation.AnalysisTier
import cranium.core.models.connotation.SentimentAnalysisOutcome
import cranium.core.models.connotation.SentimentFailureReason
import cranium.core.models.connotation.SentimentLabel
import cranium.core.models.connotation.SentimentMetadata
import java.time.ZonedDateTime

/**
 * Pure DETERMINISTIC-tier sentiment mapper.
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
class DeterministicConnotationMapper: AbstractConnotationMapper{

    override fun analyze(
        signals: ConnotationSignals,
        sourceValue: JsonValue,
        themeValues: Map<String, JsonValue>,
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

        val metadata = SentimentMetadata(
            sentiment = score,
            sentimentLabel = labelFor(score),
            themes = themes.map { it.toString() },
            analysisVersion = activeVersion,
            analysisModel = modelIdentifier(signals.sentimentScale.mappingType, activeVersion),
            analysisTier = AnalysisTier.DETERMINISTIC,
            status = ConnotationStatus.ANALYZED,
            analyzedAt = ZonedDateTime.now(),
        )
        return SentimentAnalysisOutcome.Success(metadata)
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
        "deterministic-connotation-${mappingType.name.lowercase()}-$version"
}
