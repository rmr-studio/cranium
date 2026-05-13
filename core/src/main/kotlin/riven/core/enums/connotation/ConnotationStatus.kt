package cranium.core.enums.connotation

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Lifecycle status of the SENTIMENT metadata category on a connotation snapshot.
 *
 * Scoped to SENTIMENT only — RELATIONAL and STRUCTURAL categories are deterministic
 * and either present or not (no per-category status). Documented in the eng review
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
