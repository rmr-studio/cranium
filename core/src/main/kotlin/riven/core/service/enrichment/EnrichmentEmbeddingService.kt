package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.entity.enrichment.EntityEmbeddingEntity
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.models.enrichment.EnrichmentContext
import riven.core.repository.enrichment.EntityEmbeddingRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.enrichment.provider.EmbeddingProvider
import riven.core.util.ServiceUtil
import java.util.UUID

/**
 * Service responsible for generating and persisting entity embeddings.
 *
 * Owns the embedding half of the enrichment pipeline: builds semantic text from a pre-resolved
 * [EnrichmentContext], generates a vector via [EmbeddingProvider], upserts the embedding record
 * (delete + insert pattern), and marks the execution queue item as COMPLETED.
 *
 * This service is invoked by [riven.core.service.workflow.enrichment.EnrichmentActivitiesImpl]
 * as the implementation of the `embedAndStore` Temporal activity. All three steps run in a
 * single transaction: if the queue-completion save fails, the embedding row is rolled back and
 * Temporal will retry the activity.
 *
 * Phase 1 note: collapses the former `constructEnrichedText` + `generateEmbedding` + `storeEmbedding`
 * Temporal activities into a single service method, matching the 2-activity Temporal interface shape.
 */
@Service
class EnrichmentEmbeddingService(
    private val semanticTextBuilderService: SemanticTextBuilderService,
    private val embeddingProvider: EmbeddingProvider,
    private val entityEmbeddingRepository: EntityEmbeddingRepository,
    private val executionQueueRepository: ExecutionQueueRepository,
    private val enrichmentProperties: EnrichmentConfigurationProperties,
    private val logger: KLogger,
) {

    // ------ Public API ------

    /**
     * Builds semantic text from the enrichment context, generates a vector embedding, upserts
     * the embedding record (delete then insert), and marks the queue item as COMPLETED.
     *
     * The three steps run in a single transaction: if queue completion fails the embedding row
     * is rolled back and Temporal retries the activity.
     *
     * @param context The enrichment context snapshot (provides entity/type IDs, schema version)
     * @param queueItemId The queue item to mark completed after successful embedding storage
     * @throws IllegalArgumentException if the generated embedding dimensions do not match configured vectorDimensions
     * @throws riven.core.exceptions.NotFoundException if the queue item does not exist
     */
    @Transactional
    fun embedAndStore(context: EnrichmentContext, queueItemId: UUID) {
        val result = semanticTextBuilderService.buildText(context)
        val embedding = embeddingProvider.generateEmbedding(result.text)

        validateEmbeddingDimensions(embedding)
        upsertEmbedding(context, embedding, result.truncated)
        completeQueueItem(queueItemId)

        logger.info { "Stored embedding for entity ${context.entityId}, queue item $queueItemId completed" }
    }

    // ------ Private Helpers ------

    /**
     * Validates that the generated embedding dimensions match the configured vector dimensions.
     */
    private fun validateEmbeddingDimensions(embedding: FloatArray) {
        require(embedding.size == enrichmentProperties.vectorDimensions) {
            "Embedding size ${embedding.size} does not match configured vector dimensions ${enrichmentProperties.vectorDimensions}"
        }
    }

    /**
     * Upserts the embedding record using a delete + insert pattern to handle the one-row-per-entity invariant.
     */
    private fun upsertEmbedding(context: EnrichmentContext, embedding: FloatArray, truncated: Boolean) {
        entityEmbeddingRepository.deleteByEntityId(context.entityId)
        entityEmbeddingRepository.save(
            EntityEmbeddingEntity(
                workspaceId = context.workspaceId,
                entityId = context.entityId,
                entityTypeId = context.entityTypeId,
                embedding = embedding,
                embeddingModel = embeddingProvider.getModelName(),
                schemaVersion = context.schemaVersion,
                truncated = truncated,
            )
        )
    }

    /**
     * Marks the execution queue item as COMPLETED.
     */
    private fun completeQueueItem(queueItemId: UUID) {
        val queueItem = ServiceUtil.findOrThrow { executionQueueRepository.findById(queueItemId) }
        executionQueueRepository.save(queueItem.copy(status = ExecutionQueueStatus.COMPLETED))
    }
}
