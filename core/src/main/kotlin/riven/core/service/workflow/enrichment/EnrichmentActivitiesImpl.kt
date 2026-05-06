package riven.core.service.workflow.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Component
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.service.enrichment.EnrichmentAnalysisService
import riven.core.service.enrichment.EnrichmentEmbeddingService
import java.util.UUID

/**
 * Temporal activity implementation for the entity embedding enrichment pipeline.
 *
 * This bean is a thin delegation layer — all business logic lives in the two injected services.
 * No enrichment or persistence logic belongs here.
 *
 * Exceptions are intentionally NOT caught — they propagate to Temporal for retry
 * according to the retry policy configured in [EnrichmentWorkflowImpl].
 *
 * Registered on [riven.core.configuration.workflow.TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE]
 * task queue by [riven.core.configuration.workflow.TemporalWorkerConfiguration].
 *
 * Plan 02-03: signature cascade — [analyzeSemantics] now returns [EntityKnowledgeView];
 * [embedAndStore] takes [EntityKnowledgeView] instead of [EnrichmentContext].
 *
 * @property enrichmentAnalysisService manages queue lifecycle, context assembly,
 *   and connotation snapshot persistence
 * @property enrichmentEmbeddingService manages text building, embedding generation, and queue completion
 */
@Component
class EnrichmentActivitiesImpl(
    private val enrichmentAnalysisService: EnrichmentAnalysisService,
    private val enrichmentEmbeddingService: EnrichmentEmbeddingService,
    private val logger: KLogger,
) : EnrichmentActivities {

    override fun analyzeSemantics(queueItemId: UUID): EntityKnowledgeView {
        logger.info { "AnalyzeSemantics activity: queueItemId=$queueItemId" }
        return enrichmentAnalysisService.analyzeSemantics(queueItemId)
    }

    override fun embedAndStore(view: EntityKnowledgeView, queueItemId: UUID) {
        logger.info { "EmbedAndStore activity: queueItemId=$queueItemId entityId=${view.entityId}" }
        enrichmentEmbeddingService.embedAndStore(view, queueItemId)
    }

    override fun markQueueItemFailed(queueItemId: UUID, reason: String) {
        logger.warn { "MarkQueueItemFailed activity: queueItemId=$queueItemId reason=$reason" }
        enrichmentEmbeddingService.markQueueItemFailed(queueItemId, reason)
    }
}
