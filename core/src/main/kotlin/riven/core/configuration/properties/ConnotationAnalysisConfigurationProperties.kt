package cranium.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Active sentiment-analysis versions per tier.
 *
 * The Deterministic mapper stamps [deterministicCurrentVersion] onto every [cranium.core.models.connotation.SentimentMetadata] it
 * produces. The reanalyze admin op uses these values to identify version-mismatch
 * rows and re-enqueue them for fresh analysis.
 *
 * Discovered via `@ConfigurationPropertiesScan` declared on `CoreApplication`.
 */
@ConfigurationProperties(prefix = "cranium.connotation")
data class ConnotationAnalysisConfigurationProperties(
    val deterministicCurrentVersion: String = "v1",
    val classifierCurrentVersion: String? = null,
    val inferenceCurrentVersion: String? = null,
)
