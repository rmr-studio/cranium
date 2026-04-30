package riven.core.models.connotation

/**
 * Outcome of a single DETERMINISTIC sentiment-analysis attempt.
 *
 * - [Success] carries a fully populated [SentimentMetadata] with status `ANALYZED`.
 * - [Failure] carries a typed reason for `FAILED` status; the caller is responsible
 *   for logging/metrics and constructing a sentinel `SentimentMetadata(status = FAILED, ...)`.
 */
sealed class SentimentAnalysisOutcome {
    data class Success(val metadata: SentimentMetadata) : SentimentAnalysisOutcome()

    data class Failure(val reason: SentimentFailureReason, val message: String) :
        SentimentAnalysisOutcome()
}

enum class SentimentFailureReason {
    MISSING_SOURCE_ATTRIBUTE,
    NON_NUMERIC_SOURCE_VALUE,
    INVALID_SCALE_CONFIG,
    UNSUPPORTED_MAPPING_TYPE,
}
