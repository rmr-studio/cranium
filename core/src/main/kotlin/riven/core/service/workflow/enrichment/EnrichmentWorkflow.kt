package cranium.core.service.workflow.enrichment

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.util.UUID

/**
 * Temporal workflow interface for the entity embedding enrichment pipeline.
 *
 * Orchestrates four independently retryable activities:
 * 1. FetchEntityContext — claims queue item and loads entity snapshot
 * 2. ConstructEnrichedText — assembles semantic text from context
 * 3. GenerateEmbedding — calls EmbeddingProvider to produce a vector
 * 4. StoreEmbedding — upserts vector and marks queue COMPLETED
 *
 * **Workflow ID convention:** Callers MUST set the workflow ID using the companion helper
 * to ensure Temporal-level deduplication for the same queue item:
 * ```kotlin
 * WorkflowOptions.newBuilder()
 *     .setWorkflowId(EnrichmentWorkflow.workflowId(queueItemId))
 *     .build()
 * ```
 *
 * @see EnrichmentWorkflowImpl
 */
@WorkflowInterface
interface EnrichmentWorkflow {

    /**
     * Runs the full enrichment pipeline for the given queue item.
     *
     * @param queueItemId the enrichment queue row to process
     */
    @WorkflowMethod
    fun embed(queueItemId: UUID)

    companion object {
        /**
         * Derives the canonical Temporal workflow ID for an enrichment run.
         *
         * @param queueItemId the queue item being processed
         * @return workflow ID in the format "enrichment-embed-{queueItemId}"
         */
        fun workflowId(queueItemId: UUID): String = "enrichment-embed-$queueItemId"
    }
}
