package riven.core.service.workflow.enrichment

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import riven.core.models.enrichment.EnrichmentContext
import java.util.UUID

/**
 * Temporal activity interface for the entity embedding enrichment pipeline.
 *
 * Declares three independently retryable steps:
 * 1. [analyzeSemantics] — claims the queue item, assembles the entity context, resolves
 *    sentiment metadata, and persists the polymorphic semantic snapshot (`entity_connotation`).
 *    Implemented by [riven.core.service.enrichment.EnrichmentAnalysisService].
 * 2. [embedAndStore] — builds semantic text from the context, generates a vector via the
 *    configured [riven.core.service.enrichment.provider.EmbeddingProvider], upserts the
 *    embedding record, and marks the queue item COMPLETED.
 *    Implemented by [riven.core.service.enrichment.EnrichmentEmbeddingService].
 * 3. [markQueueItemFailed] — explicit terminal-state transition the workflow invokes when the
 *    primary embedding consumer throws after Temporal's per-activity retries are exhausted.
 *    This is the only path that records FAILED on the queue row from the workflow level —
 *    sibling consumers do not transition queue state on failure (their errors are isolated
 *    per ENRICH-05). Implemented by [riven.core.service.enrichment.EnrichmentEmbeddingService].
 *
 * The former four-activity interface (analyzeSemantics + constructEnrichedText + generateEmbedding +
 * storeEmbedding) was collapsed to two activities in Plan 01-03; PR feedback r3180290311 added
 * the third (markQueueItemFailed) to close the queue-completion-path swallow gap. Post-analyze
 * consumers (embedding and future: synthesis, JSONB projection) are fan-out via [ConsumerActivity]
 * inside [EnrichmentWorkflowImpl].
 *
 * Registered on [riven.core.configuration.workflow.TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE].
 *
 * @see EnrichmentActivitiesImpl
 */
@ActivityInterface
interface EnrichmentActivities {

    /**
     * Claims the queue item, persists the polymorphic semantic snapshot to `entity_connotation`,
     * and returns a transient [EnrichmentContext] for downstream consumer activities.
     */
    @ActivityMethod
    fun analyzeSemantics(queueItemId: UUID): EnrichmentContext

    /**
     * Builds semantic text from the context, generates a vector embedding, upserts the embedding
     * record (delete + insert pattern), and marks the queue item as COMPLETED.
     */
    @ActivityMethod
    fun embedAndStore(context: EnrichmentContext, queueItemId: UUID)

    /**
     * Records a terminal FAILED state on the queue row for the primary embedding consumer's
     * post-retry failure. Idempotent: a row already in FAILED or COMPLETED is left untouched.
     *
     * Invoked by [EnrichmentWorkflowImpl.embed] from the primary-consumer catch arm so that a
     * terminal embedding failure surfaces as FAILED on the queue row instead of leaving it
     * stuck in CLAIMED.
     *
     * @param queueItemId Row to transition.
     * @param reason Free-text failure reason persisted to `last_error` for diagnosis.
     */
    @ActivityMethod
    fun markQueueItemFailed(queueItemId: UUID, reason: String)
}
