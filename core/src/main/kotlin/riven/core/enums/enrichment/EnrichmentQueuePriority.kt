package riven.core.enums.enrichment

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Priority level for enrichment queue items.
 */
enum class EnrichmentQueuePriority {
    /** Standard priority for individual entity changes */
    @JsonProperty("NORMAL")
    NORMAL,

    /** Lower priority for bulk/batch enrichment operations */
    @JsonProperty("BATCH")
    BATCH
}
