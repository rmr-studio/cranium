package riven.core.service.enrichment

import riven.core.models.enrichment.EnrichmentContext
import riven.core.service.workflow.enrichment.ConsumerActivity
import riven.core.service.workflow.enrichment.EnrichmentActivities
import java.util.UUID

/**
 * Consumer fan-out wrapper that delegates to the `embedAndStore` Temporal activity.
 *
 * Constructed inside [riven.core.service.workflow.enrichment.EnrichmentWorkflowImpl] from the
 * activity stub (workflow-deterministic). NOT a Spring bean — Temporal manages its lifecycle
 * inside the workflow body.
 *
 * Phase 1 ships exactly one consumer; future phases (Synthesis, JSONB projection) add sibling
 * implementations without modifying the workflow orchestration.
 *
 * Exceptions propagate to the workflow's [runCatching] wrapper in
 * [riven.core.service.workflow.enrichment.EnrichmentWorkflowImpl.embed], which logs a warning
 * and continues to the next consumer (Decision 4-ii.A, ENRICH-05 semantics).
 */
class EmbeddingConsumer(private val activities: EnrichmentActivities) : ConsumerActivity {
    override fun run(context: EnrichmentContext, queueItemId: UUID) {
        activities.embedAndStore(context, queueItemId)
    }
}
