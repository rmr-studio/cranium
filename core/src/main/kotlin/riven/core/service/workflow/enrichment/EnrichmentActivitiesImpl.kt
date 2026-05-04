package riven.core.service.workflow.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Component
import riven.core.models.enrichment.EnrichmentContext
import riven.core.service.enrichment.EnrichmentEmbeddingService
import riven.core.service.enrichment.EnrichmentService
import java.util.UUID

/**
 * Temporal activity implementation for the entity embedding enrichment pipeline.
 *
 * This bean is a thin delegation layer — all business logic lives in the injected services.
 * No enrichment or persistence logic belongs here.
 *
 * Exceptions are intentionally NOT caught — they propagate to Temporal for retry
 * according to the retry policy configured in [EnrichmentWorkflowImpl].
 *
 * Registered on [riven.core.configuration.workflow.TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE]
 * task queue by [riven.core.configuration.workflow.TemporalWorkerConfiguration].
 *
 * TRANSITIONAL NOTE (Plan 03 Task 1): [enrichmentService] is a transient dependency.
 * Task 2 will swap it for [riven.core.service.enrichment.EnrichmentAnalysisService] once that
 * service is extracted. [enrichmentEmbeddingService] is the final dependency shape.
 *
 * @property enrichmentService manages analysis (transitional — replaced by EnrichmentAnalysisService in Task 2)
 * @property enrichmentEmbeddingService manages text building, embedding generation, and queue completion
 */
@Component
class EnrichmentActivitiesImpl(
    private val enrichmentService: EnrichmentService,
    private val enrichmentEmbeddingService: EnrichmentEmbeddingService,
    private val logger: KLogger,
) : EnrichmentActivities {

    override fun analyzeSemantics(queueItemId: UUID): EnrichmentContext {
        logger.info { "AnalyzeSemantics activity: queueItemId=$queueItemId" }
        return enrichmentService.analyzeSemantics(queueItemId)
    }

    override fun embedAndStore(context: EnrichmentContext, queueItemId: UUID) {
        logger.info { "EmbedAndStore activity: queueItemId=$queueItemId entityId=${context.entityId}" }
        enrichmentEmbeddingService.embedAndStore(context, queueItemId)
    }
}
