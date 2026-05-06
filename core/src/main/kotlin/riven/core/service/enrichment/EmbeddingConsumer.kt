package riven.core.service.enrichment

import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.service.workflow.enrichment.ConsumerActivity
import riven.core.service.workflow.enrichment.EnrichmentActivities
import java.util.UUID

/**
 * **Primary** completion consumer that delegates to the `embedAndStore` Temporal activity.
 *
 * Constructed inside [riven.core.service.workflow.enrichment.EnrichmentWorkflowImpl] from the
 * activity stub (workflow-deterministic). NOT a Spring bean — Temporal manages its lifecycle
 * inside the workflow body.
 *
 * This consumer is NOT a sibling. It owns the queue-completion path (`embedAndStore` marks the
 * row COMPLETED on success). When it throws after Temporal retries are exhausted, the workflow
 * catches the failure and invokes [riven.core.service.workflow.enrichment.EnrichmentActivities.markQueueItemFailed]
 * to record an explicit terminal FAILED transition (PR feedback r3180290311). The failure is not
 * silently swallowed into the sibling-isolation runCatching loop.
 *
 * Plan 02-03: parameter type changed from [EnrichmentContext] to [EntityKnowledgeView] throughout
 * the consumer chain.
 *
 * Future phases (Synthesis, JSONB projection) ship additional **sibling** consumers via
 * [riven.core.service.workflow.enrichment.EnrichmentWorkflowImpl.buildConsumers] — those are
 * isolated per ENRICH-05 and do not mutate queue state.
 */
class EmbeddingConsumer(private val activities: EnrichmentActivities) : ConsumerActivity {
    override fun run(view: EntityKnowledgeView, queueItemId: UUID) {
        activities.embedAndStore(view, queueItemId)
    }
}
