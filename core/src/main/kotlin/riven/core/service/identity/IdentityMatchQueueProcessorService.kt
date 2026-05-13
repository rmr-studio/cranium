package cranium.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowExecutionAlreadyStarted
import io.temporal.client.WorkflowOptions
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import cranium.core.configuration.workflow.TemporalWorkerConfiguration
import cranium.core.enums.workflow.ExecutionJobType
import cranium.core.entity.workflow.ExecutionQueueEntity
import cranium.core.repository.workflow.ExecutionQueueRepository
import cranium.core.service.workflow.identity.IdentityMatchWorkflow
import cranium.core.service.workflow.queue.WorkflowExecutionQueueService
import java.util.UUID

/**
 * Processes individual IDENTITY_MATCH queue items in isolated transactions.
 *
 * Each item is processed in its own transaction (REQUIRES_NEW), ensuring that
 * - Row locks are released as soon as each item completes
 * - Failures in one item don't affect others
 * - Long-running Temporal dispatches don't block the entire batch
 *
 * Flow per item:
 * 1. Mark CLAIMED
 * 2. Start IdentityMatchWorkflow on the identity.match task queue via WorkflowClient
 * 3. Mark DISPATCHED on success
 * 4. If WorkflowExecutionAlreadyStarted: mark DISPATCHED (workflow already running — safe idempotency)
 * 5. On general exception: release to PENDING (for retry) or mark FAILED (at max attempts)
 */
@Service
class IdentityMatchQueueProcessorService(
    private val workflowExecutionQueueService: WorkflowExecutionQueueService,
    private val executionQueueRepository: ExecutionQueueRepository,
    private val workflowClient: WorkflowClient,
    private val logger: KLogger,
) {

    companion object {
        /** Maximum dispatch attempts before marking FAILED */
        const val MAX_ATTEMPTS = 3
    }

    // ------ Public batch operations ------

    /**
     * Claim a batch of pending IDENTITY_MATCH jobs.
     *
     * Runs in its own transaction to atomically claim rows via SKIP LOCKED.
     * Lock is released when this method returns, before processing begins.
     *
     * @param size Maximum number of items to claim.
     */
    @Transactional
    fun claimBatch(size: Int): List<ExecutionQueueEntity> {
        return executionQueueRepository.claimPendingByJobType(ExecutionJobType.IDENTITY_MATCH, size)
    }

    // ------ Public mutations ------

    /**
     * Process a single IDENTITY_MATCH queue item in its own transaction.
     *
     * Each call runs in an independent transaction (REQUIRES_NEW) so that
     * success or failure of one item does not affect other items in the batch.
     *
     * @param item The queue entity to process.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processItem(item: ExecutionQueueEntity) {
        try {
            processQueueItem(item)
        } catch (e: Exception) {
            handleProcessingError(item, e)
        }
    }

    // ------ Private helpers ------

    /**
     * Core dispatch logic for a single queue item.
     *
     * Marks the item claimed, creates a workflow stub on the identity.match task queue,
     * starts the workflow asynchronously, then marks the item dispatched.
     */
    private fun processQueueItem(item: ExecutionQueueEntity) {
        workflowExecutionQueueService.markClaimed(item)

        val entityId = requireNotNull(item.entityId) { "IDENTITY_MATCH job must have entityId" }

        val workflowStub = workflowClient.newWorkflowStub(
            IdentityMatchWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setWorkflowId(IdentityMatchWorkflow.workflowId(entityId))
                .setTaskQueue(TemporalWorkerConfiguration.IDENTITY_MATCH_QUEUE)
                .build()
        )

        try {
            WorkflowClient.start { workflowStub.matchEntity(entityId, item.workspaceId, null) }
            workflowExecutionQueueService.markDispatched(item)
            logger.debug { "Dispatched IdentityMatchWorkflow for entity $entityId (queue item ${item.id})" }
        } catch (e: WorkflowExecutionAlreadyStarted) {
            // A workflow for this entity is already running — release back to PENDING so the
            // event is retried once the current workflow completes, rather than being dropped.
            logger.info { "IdentityMatchWorkflow already running for entity $entityId — releasing to PENDING for retry" }
            workflowExecutionQueueService.releaseToPending(item)
        }
    }

    /**
     * Handle a processing error for a queue item.
     *
     * Releases to PENDING for retry if below [MAX_ATTEMPTS], otherwise marks FAILED.
     */
    private fun handleProcessingError(item: ExecutionQueueEntity, error: Exception) {
        logger.error(error) { "Error processing IDENTITY_MATCH queue item ${item.id}" }

        if (item.attemptCount >= MAX_ATTEMPTS) {
            workflowExecutionQueueService.markFailed(item, error.message ?: "Unknown error")
        } else {
            workflowExecutionQueueService.releaseToPending(item)
        }
    }
}
