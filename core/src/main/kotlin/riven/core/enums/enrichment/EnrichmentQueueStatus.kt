package riven.core.enums.enrichment

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Status of an enrichment queue item.
 *
 * Lifecycle: PENDING -> CLAIMED -> DISPATCHED -> COMPLETED (or FAILED)
 */
enum class EnrichmentQueueStatus {
    /** Waiting to be processed by enrichment dispatcher */
    @JsonProperty("PENDING")
    PENDING,

    /** Being processed by dispatcher (claimed via SKIP LOCKED) */
    @JsonProperty("CLAIMED")
    CLAIMED,

    /** Successfully dispatched to enrichment pipeline */
    @JsonProperty("DISPATCHED")
    DISPATCHED,

    /** Enrichment completed successfully */
    @JsonProperty("COMPLETED")
    COMPLETED,

    /** Failed after max retries or permanent error */
    @JsonProperty("FAILED")
    FAILED
}
