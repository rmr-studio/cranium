package riven.core.enums.workflow

/**
 * Status of an execution queue item.
 *
 * Two distinct lifecycles share the same enum:
 *
 * - Dispatcher flow: PENDING -> CLAIMED -> DISPATCHED (or FAILED). Jobs picked up by the
 *   centralised dispatcher transition to DISPATCHED once handed off to Temporal.
 * - Enrichment flow: PENDING -> CLAIMED -> COMPLETED (or FAILED). Enrichment jobs skip
 *   the DISPATCHED state because [riven.core.service.enrichment.EnrichmentService] starts
 *   the Temporal workflow directly and the [riven.core.service.workflow.enrichment.EnrichmentWorkflow]
 *   storeEmbedding activity marks the queue item COMPLETED on success.
 */
enum class ExecutionQueueStatus {
    /** Waiting to be processed by dispatcher */
    PENDING,
    /** Being processed by dispatcher (claimed via SKIP LOCKED) */
    CLAIMED,
    /** Successfully sent to Temporal */
    DISPATCHED,
    /** Completed successfully (used by enrichment jobs) */
    COMPLETED,
    /** Failed after max retries or permanent error */
    FAILED
}
