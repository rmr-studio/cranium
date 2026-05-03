package riven.core.service.workflow.enrichment

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.enrichment.EnrichmentContext
import java.util.UUID

/**
 * Temporal activity interface for the entity embedding enrichment pipeline.
 *
 * Declares four independently retryable steps:
 * 1. [analyzeSemantics] — claims queue item, assembles the entity context, and persists the
 *    polymorphic semantic snapshot (`entity_connotation`).
 * 2. [constructEnrichedText] — builds semantic text from context
 * 3. [generateEmbedding] — calls EmbeddingProvider to produce a vector
 * 4. [storeEmbedding] — upserts the vector and marks queue COMPLETED
 *
 * Registered on [riven.core.configuration.workflow.TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE].
 *
 * @see EnrichmentActivitiesImpl
 */
@ActivityInterface
interface EnrichmentActivities {

    /**
     * Claims the queue item, persists the polymorphic semantic snapshot to `entity_connotation`,
     * and returns a transient [EnrichmentContext] for downstream activities.
     */
    @ActivityMethod
    fun analyzeSemantics(queueItemId: UUID): EnrichmentContext

    /** Assembles semantic text sections from the enrichment context. Returns text with truncation metadata. */
    @ActivityMethod
    fun constructEnrichedText(context: EnrichmentContext): EnrichedTextResult

    /** Calls the configured [riven.core.service.enrichment.provider.EmbeddingProvider] to produce a vector. */
    @ActivityMethod
    fun generateEmbedding(text: String): FloatArray

    /** Upserts the embedding record and marks the queue item as COMPLETED. */
    @ActivityMethod
    fun storeEmbedding(queueItemId: UUID, context: EnrichmentContext, embedding: FloatArray, truncated: Boolean)
}
