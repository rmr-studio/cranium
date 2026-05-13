package cranium.core.enums.identity

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The mechanism by which a candidate match row was produced.
 *
 * Carried on [cranium.core.models.identity.CandidateMatch] and propagated through to
 * [cranium.core.models.identity.MatchSignal] so that the scoring service can apply
 * source-specific adjustments (e.g. exact-digits matches receive a higher confidence
 * floor than trigram approximations).
 */
enum class MatchSource {
    @JsonProperty("TRIGRAM")
    TRIGRAM,

    @JsonProperty("EXACT_NORMALIZED")
    EXACT_NORMALIZED,

    @JsonProperty("NICKNAME")
    NICKNAME,

    @JsonProperty("EMAIL_DOMAIN")
    EMAIL_DOMAIN,

    @JsonProperty("PHONETIC")
    PHONETIC,
}
