package riven.core.service.workflow.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Component
import riven.core.models.enrichment.EnrichedTextResult
import riven.core.models.enrichment.EnrichmentContext
import riven.core.service.enrichment.EnrichmentService
import riven.core.service.enrichment.SemanticTextBuilderService
import riven.core.service.enrichment.provider.EmbeddingProvider
import java.util.UUID

/**
 * Temporal activity implementation for the entity embedding enrichment pipeline.
 *
 * This bean is a thin delegation layer — all business logic lives in the three
 * injected services. No enrichment or persistence logic belongs here.
 *
 * Exceptions are intentionally NOT caught — they propagate to Temporal for retry
 * according to the retry policy configured in [EnrichmentWorkflowImpl].
 *
 * Registered on [riven.core.configuration.workflow.TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE]
 * task queue by [riven.core.configuration.workflow.TemporalWorkerConfiguration].
 *
 * @property enrichmentService manages queue lifecycle and context assembly
 * @property semanticTextBuilderService constructs human-readable enriched text
 * @property embeddingProvider generates the embedding vector via the configured provider
 */
@Component
class EnrichmentActivitiesImpl(
    private val enrichmentService: EnrichmentService,
    private val semanticTextBuilderService: SemanticTextBuilderService,
    private val embeddingProvider: EmbeddingProvider,
    private val logger: KLogger,
) : EnrichmentActivities {

    override fun fetchEntityContext(queueItemId: UUID): EnrichmentContext {
        logger.info { "FetchEntityContext activity: queueItemId=$queueItemId" }
        return enrichmentService.fetchContext(queueItemId)
    }

    override fun constructEnrichedText(context: EnrichmentContext): EnrichedTextResult {
        logger.info { "ConstructEnrichedText activity: entityId=${context.entityId}" }
        return semanticTextBuilderService.buildText(context)
    }

    override fun generateEmbedding(text: String): FloatArray {
        logger.info { "GenerateEmbedding activity: textLength=${text.length} model=${embeddingProvider.getModelName()}" }
        return embeddingProvider.generateEmbedding(text)
    }

    override fun storeEmbedding(queueItemId: UUID, context: EnrichmentContext, embedding: FloatArray, truncated: Boolean) {
        logger.info { "StoreEmbedding activity: queueItemId=$queueItemId entityId=${context.entityId} dimensions=${embedding.size} truncated=$truncated" }
        enrichmentService.storeEmbedding(queueItemId, context, embedding, truncated)
    }
}
