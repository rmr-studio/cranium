package cranium.core.service.workflow.queue

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.workflow.ExecutionQueueEntity
import cranium.core.enums.workflow.ExecutionJobType
import cranium.core.enums.workflow.ExecutionQueueStatus
import cranium.core.exceptions.NotFoundException
import cranium.core.models.common.json.JsonObject
import cranium.core.repository.workflow.ExecutionQueueRepository
import cranium.core.repository.workflow.WorkflowDefinitionRepository
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Service for managing the workflow execution queue.
 *
 * Handles:
 * - Enqueueing new execution requests (from API)
 * - Status updates (claimed, dispatched, failed)
 * - Queue position and count queries
 * - Stale claim recovery
 *
 * Does NOT handle: Dispatching to Temporal (see ExecutionDispatcherService)
 */
@Service
class WorkflowExecutionQueueService(
    private val executionQueueRepository: ExecutionQueueRepository,
    private val workflowDefinitionRepository: WorkflowDefinitionRepository,
    private val logger: KLogger
) {

    /**
     * Enqueue a workflow execution request.
     *
     * Creates a PENDING queue item that will be processed by the dispatcher.
     * Returns immediately - actual execution happens asynchronously.
     *
     * @param workspaceId Workspace context
     * @param workflowDefinitionId Workflow to execute
     * @param params Optional input parameters
     * @return Created queue entity with ID
     * @throws NotFoundException if workflow definition not found
     * @throws SecurityException if workflow doesn't belong to workspace
     */
    @Transactional
    fun enqueue(
        workspaceId: UUID,
        workflowDefinitionId: UUID,
        params: JsonObject? = null
    ): ExecutionQueueEntity {
        logger.info { "Enqueueing workflow execution: definition=$workflowDefinitionId, workspace=$workspaceId" }

        // Validate workflow definition exists and belongs to workspace
        val definition = workflowDefinitionRepository.findById(workflowDefinitionId)
            .orElseThrow { NotFoundException("Workflow definition not found: $workflowDefinitionId") }

        if (definition.workspaceId != workspaceId) {
            throw SecurityException("Workflow definition $workflowDefinitionId does not belong to workspace $workspaceId")
        }

        val queueEntity = ExecutionQueueEntity(
            workspaceId = workspaceId,
            jobType = ExecutionJobType.WORKFLOW_EXECUTION,
            workflowDefinitionId = workflowDefinitionId,
            status = ExecutionQueueStatus.PENDING,
            createdAt = ZonedDateTime.now(),
        )

        val saved = executionQueueRepository.save(queueEntity)
        logger.info { "Enqueued execution: id=${saved.id}" }

        return saved
    }

    /**
     * Mark queue item as claimed.
     *
     * Called by dispatcher after claiming via SKIP LOCKED.
     * Sets status to CLAIMED and records claim time.
     *
     * @param entity Entity to mark claimed
     * @return Updated entity
     */
    @Transactional
    fun markClaimed(entity: ExecutionQueueEntity): ExecutionQueueEntity {
        entity.status = ExecutionQueueStatus.CLAIMED
        entity.claimedAt = ZonedDateTime.now()
        return executionQueueRepository.save(entity)
    }

    /**
     * Mark queue item as dispatched.
     *
     * Called by dispatcher after successful Temporal workflow start.
     *
     * @param entity Entity to mark dispatched
     * @return Updated entity
     */
    @Transactional
    fun markDispatched(entity: ExecutionQueueEntity): ExecutionQueueEntity {
        entity.status = ExecutionQueueStatus.DISPATCHED
        entity.dispatchedAt = ZonedDateTime.now()
        return executionQueueRepository.save(entity)
    }

    /**
     * Mark queue item as failed.
     *
     * Called by dispatcher when execution cannot proceed (permanent error or max retries).
     *
     * @param entity Entity to mark failed
     * @param error Error message
     * @return Updated entity
     */
    @Transactional
    fun markFailed(entity: ExecutionQueueEntity, error: String): ExecutionQueueEntity {
        entity.status = ExecutionQueueStatus.FAILED
        entity.attemptCount = entity.attemptCount + 1
        entity.lastError = error
        return executionQueueRepository.save(entity)
    }

    /**
     * Set the execution ID on a queue item.
     *
     * Called by dispatcher after creating/loading a WorkflowExecutionEntity
     * but before starting the Temporal workflow. This ensures the execution
     * can be reused on retry if Temporal start fails.
     *
     * @param entity Entity to update
     * @param executionId The workflow execution ID to associate
     * @return Updated entity
     */
    @Transactional
    fun setExecutionId(entity: ExecutionQueueEntity, executionId: UUID): ExecutionQueueEntity {
        entity.executionId = executionId
        return executionQueueRepository.save(entity)
    }

    /**
     * Release a claimed item back to pending (for retry).
     *
     * Used when dispatch fails but retry is possible.
     *
     * @param entity Entity to release
     * @return Updated entity
     */
    @Transactional
    fun releaseToPending(entity: ExecutionQueueEntity): ExecutionQueueEntity {
        entity.status = ExecutionQueueStatus.PENDING
        entity.claimedAt = null
        entity.attemptCount = entity.attemptCount + 1
        return executionQueueRepository.save(entity)
    }

    /**
     * Get pending queue count for a workspace.
     *
     * @param workspaceId Workspace to query
     * @return Number of pending executions
     */
    @Transactional(readOnly = true)
    fun getPendingCount(workspaceId: UUID): Int {
        return executionQueueRepository.countByWorkspaceIdAndStatus(
            workspaceId,
            ExecutionQueueStatus.PENDING
        )
    }

    /**
     * Recover stale claimed items.
     *
     * Items claimed but not dispatched within timeout are released back to PENDING.
     * Prevents items from being stuck after dispatcher crashes.
     *
     * @param timeoutMinutes Threshold for stale claims
     * @return Number of items recovered
     */
    @Transactional
    fun recoverStaleItems(timeoutMinutes: Int = 5): Int {
        val staleItems = executionQueueRepository.findStaleClaimedByJobType(ExecutionJobType.WORKFLOW_EXECUTION, timeoutMinutes)

        if (staleItems.isEmpty()) {
            return 0
        }

        logger.warn { "Recovering ${staleItems.size} stale claimed items (claimed > $timeoutMinutes minutes ago)" }

        staleItems.forEach { item ->
            releaseToPending(item)
        }

        return staleItems.size
    }
}
