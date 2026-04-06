package riven.core.enums.identity

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * State machine for a match suggestion lifecycle.
 *
 * PENDING  — newly created, awaiting human review
 * CONFIRMED — user accepted the match; triggers cluster merge
 * REJECTED — user dismissed the suggestion; snapshot of signals stored for re-suggestion diff
 * EXPIRED  — suggestion aged out before review (e.g. underlying entity changed significantly)
 */
enum class MatchSuggestionStatus {
    @JsonProperty("PENDING")
    PENDING,

    @JsonProperty("CONFIRMED")
    CONFIRMED,

    @JsonProperty("REJECTED")
    REJECTED,

    @JsonProperty("EXPIRED")
    EXPIRED
}
