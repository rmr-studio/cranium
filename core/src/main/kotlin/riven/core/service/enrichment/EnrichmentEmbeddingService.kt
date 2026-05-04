package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.entity.enrichment.EntityEmbeddingEntity
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.repository.enrichment.EntityEmbeddingRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.enrichment.provider.EmbeddingProvider
import riven.core.util.ServiceUtil
import java.util.UUID

/**
 * Service responsible for generating and persisting entity embeddings.
 *
 * Owns the embedding half of the enrichment pipeline: builds semantic text from a pre-assembled
 * [EntityKnowledgeView], generates a vector via [EmbeddingProvider], upserts the embedding record
 * (delete + insert pattern), and marks the execution queue item as COMPLETED.
 *
 * This service is invoked by [riven.core.service.workflow.enrichment.EnrichmentActivitiesImpl]
 * as the implementation of the `embedAndStore` Temporal activity. All three steps run in a
 * single transaction: if the queue-completion save fails, the embedding row is rolled back and
 * Temporal will retry the activity.
 *
 * Plan 02-03: [embedAndStore] now takes [EntityKnowledgeView] instead of [EnrichmentContext].
 * The view's identifying scalars ([EntityKnowledgeView.entityId], [EntityKnowledgeView.workspaceId],
 * [EntityKnowledgeView.entityTypeId], [EntityKnowledgeView.schemaVersion]) replace the former
 * context fields for upsert and logging.
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
     * Builds semantic text from the knowledge view, generates a vector embedding, upserts
     * the embedding record (delete then insert), and marks the queue item as COMPLETED.
     *
     * The three steps run in a single transaction: if queue completion fails the embedding row
     * is rolled back and Temporal retries the activity.
     *
     * @param view The assembled entity knowledge view (provides entity/type IDs, schema version)
     * @param queueItemId The queue item to mark completed after successful embedding storage
     * @throws IllegalArgumentException if the generated embedding dimensions do not match configured vectorDimensions
     * @throws riven.core.exceptions.NotFoundException if the queue item does not exist
     */
    @Transactional
    fun embedAndStore(view: EntityKnowledgeView, queueItemId: UUID) {
        val result = semanticTextBuilderService.buildText(view)
        val embedding = embeddingProvider.generateEmbedding(result.text)

        validateEmbeddingDimensions(embedding)
        upsertEmbedding(view, embedding, result.truncated)
        completeQueueItem(queueItemId)

        logger.info { "Stored embedding for entity ${view.entityId}, queue item $queueItemId completed" }
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
    private fun upsertEmbedding(view: EntityKnowledgeView, embedding: FloatArray, truncated: Boolean) {
        entityEmbeddingRepository.deleteByEntityId(view.entityId)
        entityEmbeddingRepository.save(
            EntityEmbeddingEntity(
                workspaceId = view.workspaceId,
                entityId = view.entityId,
                entityTypeId = view.entityTypeId,
                embedding = embedding,
                embeddingModel = embeddingProvider.getModelName(),
                schemaVersion = view.schemaVersion,
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

    // ------ Terminal failure ------

    /**
     * Records a terminal FAILED transition on the queue row when the primary embedding consumer
     * exhausts Temporal retries. Idempotent: rows already in COMPLETED or FAILED are left untouched
     * so a duplicate workflow invocation cannot regress a successfully completed job, nor overwrite
     * an existing failure reason.
     *
     * Invoked from [riven.core.service.workflow.enrichment.EnrichmentWorkflowImpl.embed] via the
     * `markQueueItemFailed` Temporal activity.
     *
     * @param queueItemId Row to transition.
     * @param reason Free-text failure reason persisted to `last_error`. Truncated to 4000 chars.
     */
    @Transactional
    fun markQueueItemFailed(queueItemId: UUID, reason: String) {
        val queueItem = ServiceUtil.findOrThrow { executionQueueRepository.findById(queueItemId) }
        if (queueItem.status == ExecutionQueueStatus.COMPLETED || queueItem.status == ExecutionQueueStatus.FAILED) {
            logger.warn {
                "Skipping markQueueItemFailed for $queueItemId: already in terminal state ${queueItem.status}"
            }
            return
        }
        executionQueueRepository.save(
            queueItem.copy(
                status = ExecutionQueueStatus.FAILED,
                lastError = reason.take(MAX_LAST_ERROR_LENGTH),
            )
        )
        logger.warn { "Queue item $queueItemId marked FAILED: $reason" }
    }

    private companion object {
        private const val MAX_LAST_ERROR_LENGTH = 4000
    }
}
