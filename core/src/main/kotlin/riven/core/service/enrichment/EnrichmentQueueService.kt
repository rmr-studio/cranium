package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.entity.entity.EntityEntity
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.integration.SourceType
import riven.core.enums.workflow.ExecutionJobType
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.repository.entity.EntityRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.workflow.enrichment.EnrichmentWorkflow
import riven.core.util.ServiceUtil
import java.util.*

/**
 * Manages the entry-point lifecycle for the entity enrichment queue.
 *
 * Owns embeddability gating, queue item creation, and post-commit Temporal dispatch.
 * Extracted from [EnrichmentService] as part of the enrichment pipeline decomposition (Plan 01-02).
 *
 * Core responsibilities:
 * - [enqueueAndProcess] — embeddability gate + queue item creation + Temporal dispatch.
 * - [enqueueByEntityType] — bulk re-enrichment for every entity of a type (manifest reconciliation hook).
 *
 * Dependency ceiling: ≤ 8 constructor parameters (ENRICH-02). Current count: 4.
 */
@Service
class EnrichmentQueueService(
    private val executionQueueRepository: ExecutionQueueRepository,
    private val entityRepository: EntityRepository,
    private val workflowClient: WorkflowClient,
    private val logger: KLogger,
) {

    // ------ Public Entry Points ------

    /**
     * Enqueues an entity for enrichment and dispatches a Temporal workflow.
     *
     * INTEGRATION entities are silently skipped — they are derived from external systems
     * and are not embeddable by design. All other source types proceed through the pipeline.
     *
     * @param entityId The entity to enrich
     * @param workspaceId The workspace the entity belongs to
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun enqueueAndProcess(entityId: UUID, workspaceId: UUID) {
        val entity = ServiceUtil.findOrThrow { entityRepository.findByIdAndWorkspaceId(entityId, workspaceId) }

        if (!isEmbeddable(entity)) {
            logger.debug { "Skipping enrichment for INTEGRATION entity $entityId" }
            return
        }

        val queueItem = executionQueueRepository.save(
            ExecutionQueueEntity(
                workspaceId = workspaceId,
                jobType = ExecutionJobType.ENRICHMENT,
                entityId = entityId,
                status = ExecutionQueueStatus.PENDING,
            )
        )

        val queueItemId = requireNotNull(queueItem.id) { "Persisted ExecutionQueueEntity must have an ID" }

        startEnrichmentWorkflowAfterCommit(queueItemId)

        logger.info { "Enqueued entity $entityId for enrichment, queue item: $queueItemId" }
    }

    /**
     * Bulk-enqueue ENRICHMENT items for every non-INTEGRATION, non-deleted entity of [entityTypeId]
     * in [workspaceId]. Hooked from [riven.core.service.catalog.SchemaReconciliationService] when
     * a manifest schema change invalidates the STRUCTURAL metadata snapshots stored in
     * `entity_connotation`.
     *
     * INTEGRATION rows are deliberately excluded: per the Entity Reconsumption architecture
     * (Notion: "Entity Reconsumption, Schema Reconciliation & Breaking-Change Detection"),
     * historical/integration entities are tier 1 ("DO NOT") — source-of-truth records that are
     * never enriched directly. Only their projected representations are enriched, and a manifest
     * change to a catalog type does not invalidate snapshots that integration rows never had.
     *
     * Backed by a single `INSERT ... SELECT` in [ExecutionQueueRepository.enqueueEnrichmentByEntityType]
     * to avoid N+1 at high entity-type cardinality. The partial unique index on `execution_queue`
     * deduplicates against in-flight PENDING rows.
     *
     * Workflow dispatch is intentionally NOT triggered here — these queue items are picked up by
     * the existing enrichment dispatcher pattern.
     *
     * @return Count of rows actually inserted (excludes skipped duplicates).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun enqueueByEntityType(entityTypeId: UUID, workspaceId: UUID): Int {
        val inserted = executionQueueRepository.enqueueEnrichmentByEntityType(entityTypeId, workspaceId)
        logger.info { "Bulk-enqueued $inserted ENRICHMENT items for entity type $entityTypeId in workspace $workspaceId" }
        return inserted
    }

    // ------ Private Helpers ------

    /**
     * Registers a post-commit callback to dispatch the Temporal enrichment workflow.
     *
     * Defers workflow start until after the surrounding DB transaction commits, so the
     * queue row is guaranteed to exist before the workflow activity queries it. Prevents
     * orphaned workflows when the transaction rolls back after Temporal dispatch. When no
     * transaction is active (e.g. unit tests without a real tx), dispatches immediately
     * as a fallback — matching the pattern in
     * [riven.core.service.entity.EntityTypeSemanticMetadataService].
     */
    private fun startEnrichmentWorkflowAfterCommit(queueItemId: UUID) {
        val dispatch: () -> Unit = {
            val stub = workflowClient.newWorkflowStub(
                EnrichmentWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
                    .setWorkflowId(EnrichmentWorkflow.workflowId(queueItemId))
                    .build()
            )
            WorkflowClient.start { stub.embed(queueItemId) }
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    dispatch()
                }
            })
        } else {
            dispatch()
        }
    }

    /**
     * Returns true if the entity should be enqueued for embedding.
     *
     * INTEGRATION entities are skipped — they are synced from external systems
     * and should not be embedded directly.
     */
    private fun isEmbeddable(entity: EntityEntity): Boolean =
        entity.sourceType != SourceType.INTEGRATION
}
