package riven.core.service.workflow.queue

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.entity.workflow.execution.WorkflowExecutionEntity
import riven.core.enums.workflow.WorkflowStatus
import riven.core.enums.workflow.WorkflowTriggerType
import riven.core.enums.workspace.WorkspaceTier
import riven.core.models.common.json.JsonValue
import riven.core.models.workflow.engine.WorkflowExecutionInput
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.repository.workflow.WorkflowDefinitionRepository
import riven.core.repository.workflow.WorkflowDefinitionVersionRepository
import riven.core.repository.workflow.WorkflowExecutionRepository
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.service.workflow.engine.WorkflowOrchestration
import java.time.ZonedDateTime
import java.util.*

/**
 * Processes individual queue items in isolated transactions.
 *
 * Each item is processed in its own transaction (REQUIRES_NEW),
 * ensuring that:
 * - Row locks are released as soon as each item completes
 * - Failures in one item don't affect others
 * - Long-running dispatches don't block the entire batch
 */
@Service
class WorkflowExecutionQueueProcessorService(
    private val workflowExecutionQueueService: WorkflowExecutionQueueService,
    private val workflowExecutionRepository: WorkflowExecutionRepository,
    private val workflowDefinitionRepository: WorkflowDefinitionRepository,
    private val executionQueueRepository: ExecutionQueueRepository,
    private val workflowDefinitionVersionRepository: WorkflowDefinitionVersionRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val workflowClient: WorkflowClient,
    private val logger: KLogger
) {

    /**
     * Claim a batch of pending executions.
     *
     * Runs in its own transaction to atomically claim rows via SKIP LOCKED.
     * Lock is released when this method returns, before processing begins.
     */
    @Transactional
    fun claimBatch(size: Int): List<ExecutionQueueEntity> {
        return executionQueueRepository.claimPendingExecutions(size)
    }

    companion object {
        /** Maximum dispatch attempts before marking FAILED */
        const val MAX_ATTEMPTS = 3
    }

    /**
     * Process a single queue item in its own transaction.
     *
     * Checks workspace capacity and dispatches if available.
     * Each call runs in an independent transaction (REQUIRES_NEW).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processItem(item: ExecutionQueueEntity) {
        try {
            processQueueItem(item)
        } catch (e: Exception) {
            handleProcessingError(item, e)
        }
    }

    /**
     * Process a single queue item.
     *
     * Checks workspace capacity and dispatches if available.
     */
    private fun processQueueItem(item: ExecutionQueueEntity) {
        // Mark as claimed
        workflowExecutionQueueService.markClaimed(item)

        // Get workspace and check tier capacity
        val workspace = workspaceRepository.findById(item.workspaceId).orElse(null)
        if (workspace == null) {
            logger.warn { "Workspace ${item.workspaceId} not found, marking item ${item.id} as failed" }
            workflowExecutionQueueService.markFailed(item, "Workspace not found")
            return
        }

        val tier = WorkspaceTier.fromPlan(workspace.plan)
        val activeCount = workflowExecutionRepository.countActiveByWorkspace(item.workspaceId)

        if (activeCount >= tier.maxConcurrentWorkflows) {
            // At capacity - release back to pending for later retry
            logger.info {
                "Workspace ${item.workspaceId} at capacity ($activeCount/${tier.maxConcurrentWorkflows}), " +
                        "releasing item ${item.id} back to queue"
            }
            workflowExecutionQueueService.releaseToPending(item)
            return
        }

        // Capacity available - dispatch to Temporal
        dispatchToTemporal(item)
    }

    /**
     * Dispatch queue item to Temporal.
     *
     * Reuses existing WorkflowExecutionEntity if item.executionId is set (retry scenario),
     * otherwise creates a new one. The execution ID is persisted to the queue item before
     * starting the Temporal workflow to ensure it can be reused on retry.
     */
    private fun dispatchToTemporal(item: ExecutionQueueEntity) {
        logger.info { "Dispatching queue item ${item.id} for workflow ${item.workflowDefinitionId}" }

        // WORKFLOW_EXECUTION jobs require a workflow definition ID
        val workflowDefinitionId = item.workflowDefinitionId
        if (workflowDefinitionId == null) {
            workflowExecutionQueueService.markFailed(item, "Workflow definition ID is required for WORKFLOW_EXECUTION jobs")
            return
        }

        // Fetch workflow definition and version
        val workflowDefinition = workflowDefinitionRepository.findById(workflowDefinitionId).orElse(null)
        if (workflowDefinition == null) {
            workflowExecutionQueueService.markFailed(item, "Workflow definition not found")
            return
        }

        val workflowVersion = workflowDefinitionVersionRepository
            .findByWorkflowDefinitionIdAndVersionNumber(
                workflowDefinitionId,
                workflowDefinition.versionNumber
            )
        if (workflowVersion == null) {
            workflowExecutionQueueService.markFailed(item, "Workflow version not found")
            return
        }

        // Extract node IDs from workflow graph reference
        val nodeIds = workflowVersion.workflow.nodeIds.toList()

        // Get or create workflow execution entity
        val record = getOrCreateExecution(item, workflowDefinitionId, workflowVersion.id!!)
        val id = requireNotNull(record.id)

        // Persist execution ID to queue item before starting Temporal workflow.
        // This ensures the execution can be reused on retry if Temporal start fails.
        // Always update if different (handles case where previous execution was deleted).
        if (item.executionId != id) {
            workflowExecutionQueueService.setExecutionId(item, id)
        }

        // Start Temporal workflow
        try {
            val workflowStub = workflowClient.newWorkflowStub(
                WorkflowOrchestration::class.java,
                WorkflowOptions.newBuilder()
                    .setWorkflowId("execution-$id")
                    .setTaskQueue(TemporalWorkerConfiguration.WORKFLOWS_DEFAULT_QUEUE)
                    .build()
            )

            val workflowInput = WorkflowExecutionInput(
                workflowDefinitionId = workflowDefinitionId,
                nodeIds = nodeIds,
                workspaceId = item.workspaceId
            )

            WorkflowClient.start { workflowStub.execute(workflowInput) }

            // Mark queue item as dispatched
            workflowExecutionQueueService.markDispatched(item)

            logger.info { "Dispatched execution $id for queue item ${item.id}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to start Temporal workflow for queue item ${item.id}" }

            // Delete the execution record to avoid orphaned records.
            // The queue item still has the executionId, but it will be cleared
            // or a new execution created on retry depending on recovery logic.
            workflowExecutionRepository.deleteById(id)

            throw e // Re-throw to trigger error handling
        }
    }

    /**
     * Get existing or create new WorkflowExecutionEntity.
     *
     * If item.executionId is set (retry scenario), loads the existing execution
     * and updates its status to RUNNING. Otherwise creates a new execution.
     *
     * @param item The queue entity being processed
     * @param workflowDefinitionId The resolved (non-null) workflow definition ID
     * @param workflowVersionId The workflow version UUID to associate with the execution
     * @return The execution entity
     */
    private fun getOrCreateExecution(
        item: ExecutionQueueEntity,
        workflowDefinitionId: UUID,
        workflowVersionId: UUID
    ): WorkflowExecutionEntity {
        val existingExecutionId = item.executionId

        if (existingExecutionId != null) {
            // Retry scenario: reuse existing execution
            val existingExecution = workflowExecutionRepository.findById(existingExecutionId).orElse(null)
            if (existingExecution != null) {
                logger.info { "Reusing existing execution $existingExecutionId for queue item ${item.id}" }
                val updatedExecution = existingExecution.copy(
                    status = WorkflowStatus.RUNNING,
                    startedAt = ZonedDateTime.now(),
                    completedAt = null,
                    error = emptyMap<String, JsonValue>()
                )
                return workflowExecutionRepository.save(updatedExecution)

            }
            // Execution was deleted or not found - fall through to create new
            logger.warn { "Execution $existingExecutionId not found for queue item ${item.id}, creating new" }
        }

        // Create new execution
        val executionEntity = WorkflowExecutionEntity(
            workspaceId = item.workspaceId,
            workflowDefinitionId = workflowDefinitionId,
            workflowVersionId = workflowVersionId,

            status = WorkflowStatus.RUNNING,
            triggerType = WorkflowTriggerType.FUNCTION,
            startedAt = ZonedDateTime.now(),
            completedAt = null,
            durationMs = 0,
            error = emptyMap<String, Any>(),
            input = item.input,
            output = null
        )

        return workflowExecutionRepository.save(executionEntity)

    }

    /**
     * Handle processing error for a queue item.
     */
    private fun handleProcessingError(item: ExecutionQueueEntity, error: Exception) {
        logger.error(error) { "Error processing queue item ${item.id}" }

        if (item.attemptCount >= MAX_ATTEMPTS) {
            // Max attempts reached - mark as failed
            workflowExecutionQueueService.markFailed(item, error.message ?: "Unknown error")
        } else {
            // Release for retry
            workflowExecutionQueueService.releaseToPending(item)
        }
    }
}
