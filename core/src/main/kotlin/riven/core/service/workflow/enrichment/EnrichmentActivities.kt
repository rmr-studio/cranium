package riven.core.service.workflow.enrichment

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import riven.core.models.enrichment.EnrichmentContext
import java.util.UUID

/**
 * Temporal activity interface for the entity embedding enrichment pipeline.
 *
 * Declares two independently retryable steps:
 * 1. [analyzeSemantics] — claims the queue item, assembles the entity context, resolves
 *    sentiment metadata, and persists the polymorphic semantic snapshot (`entity_connotation`).
 *    Implemented by [riven.core.service.enrichment.EnrichmentAnalysisService].
 * 2. [embedAndStore] — builds semantic text from the context, generates a vector via the
 *    configured [riven.core.service.enrichment.provider.EmbeddingProvider], upserts the
 *    embedding record, and marks the queue item COMPLETED.
 *    Implemented by [riven.core.service.enrichment.EnrichmentEmbeddingService].
 *
 * The former four-activity interface (analyzeSemantics + constructEnrichedText + generateEmbedding +
 * storeEmbedding) is collapsed to two activities in Plan 01-03. Post-analyze consumers
 * (embedding and future: synthesis, JSONB projection) are fan-out via [ConsumerActivity]
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
}
