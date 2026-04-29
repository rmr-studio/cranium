package riven.core.enums.connotation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Lifecycle status of the SENTIMENT axis on a connotation envelope.
 *
 * Scoped to SENTIMENT only — RELATIONAL and STRUCTURAL axes are deterministic
 * and either present or not (no per-axis status). Documented in the eng review
 * addendum of the entity connotation pipeline plan.
 */
enum class ConnotationStatus {
    @JsonProperty("ANALYZED")
    ANALYZED,

    @JsonProperty("PENDING_RETRY")
    PENDING_RETRY,

    @JsonProperty("FAILED")
    FAILED,

    @JsonProperty("NOT_APPLICABLE")
    NOT_APPLICABLE,
}
