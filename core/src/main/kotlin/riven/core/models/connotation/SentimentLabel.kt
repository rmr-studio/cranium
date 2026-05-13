package cranium.core.models.connotation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Coarse-grained categorical sentiment label derived from the numeric sentiment
 */
enum class SentimentLabel {
    @JsonProperty("VERY_NEGATIVE")
    VERY_NEGATIVE,

    @JsonProperty("NEGATIVE")
    NEGATIVE,

    @JsonProperty("NEUTRAL")
    NEUTRAL,

    @JsonProperty("POSITIVE")
    POSITIVE,

    @JsonProperty("VERY_POSITIVE")
    VERY_POSITIVE,
}
