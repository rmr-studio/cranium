package cranium.core.service.workflow.engine.completion

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.enums.workflow.ExecutionQueueStatus
import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.workflow.engine.error.WorkflowExecutionError
import cranium.core.repository.workflow.ExecutionQueueRepository
import cranium.core.repository.workflow.WorkflowExecutionRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

/**
 * Spring-managed activity implementation for recording workflow completion.
 *
 * This service is called by Temporal at the end of workflow execution to persist
 * the final status. It operates independently of the dispatcher:
 *
 * - Dispatcher: PENDING → CLAIMED → DISPATCHED (owns claiming via ShedLock)
 * - This service: DISPATCHED → COMPLETED/FAILED (owns completion)
 *
 * No ShedLock or SKIP LOCKED needed here because:
 * - Targets specific records by execution_id (not bulk claiming)
 * - Each execution_id is unique (no concurrent updates possible)
 * - Runs in Temporal worker thread pool (not scheduled)
 *
 * @property workflowExecutionRepository Persistence for execution records
 * @property executionQueueRepository Persistence for queue items
 * @property logger Kotlin logging instance
 */
@Service
class WorkflowCompletionActivityImpl(
    private val workflowExecutionRepository: WorkflowExecutionRepository,
    private val executionQueueRepository: ExecutionQueueRepository,
    private val logger: KLogger
) : WorkflowCompletionActivity {

    /**
     * Record the final completion status of a workflow execution.
     *
     * Updates both the execution record and queue item in a single transaction.
     * Called by WorkflowOrchestrationService after DAG execution completes.
     *
     * @param executionId The workflow execution UUID
     * @param status Final status (COMPLETED or FAILED)
     * @param error Error details if status is FAILED, null otherwise
     */
    @Transactional
    override fun recordCompletion(
        executionId: UUID,
        status: WorkflowStatus,
        error: WorkflowExecutionError?
    ) {
        logger.info { "Recording completion for execution $executionId: status=$status" }

        // 1. Update WorkflowExecutionEntity
        val execution = workflowExecutionRepository.findById(executionId).orElse(null)

        if (execution == null) {
            // Execution record may have been deleted (e.g., during testing or cleanup)
            // Log warning but don't fail - the workflow completed regardless
            logger.warn { "Execution $executionId not found, skipping status update" }
        } else {
            val completedAt = ZonedDateTime.now()
            val durationMs = Duration.between(execution.startedAt, completedAt).toMillis()

            val updatedExecution = execution.copy(
                status = status,
                completedAt = completedAt,
                durationMs = durationMs,
                error = error?.let { mapErrorToJson(it) }
            )

            workflowExecutionRepository.save(updatedExecution)
            logger.info { "Updated execution $executionId: status=$status, duration=${durationMs}ms" }
        }

        // 2. Update queue item
        val queueItem = executionQueueRepository.findByExecutionId(executionId)

        if (queueItem == null) {
            // Queue item may have been deleted or never linked
            logger.warn { "Queue item for execution $executionId not found, skipping queue update" }
        } else {
            when (status) {
                WorkflowStatus.COMPLETED -> {
                    // Delete completed queue items to keep table small
                    // Alternative: mark as COMPLETED for audit trail
                    executionQueueRepository.delete(queueItem)
                    logger.info { "Deleted queue item ${queueItem.id} for completed execution $executionId" }
                }

                WorkflowStatus.FAILED -> {
                    // Mark queue item as failed with error message
                    queueItem.status = ExecutionQueueStatus.FAILED
                    queueItem.lastError = error?.message ?: "Workflow execution failed"
                    executionQueueRepository.save(queueItem)
                    logger.info { "Marked queue item ${queueItem.id} as FAILED for execution $executionId" }
                }

                else -> {
                    // Unexpected status - log warning but don't fail
                    logger.warn { "Unexpected completion status $status for execution $executionId" }
                }
            }
        }

        logger.info { "Completion recorded for execution $executionId" }
    }

    /**
     * Convert WorkflowExecutionError to a Map for JSONB storage.
     */
    private fun mapErrorToJson(error: WorkflowExecutionError): Map<String, Any?> {
        return mapOf(
            "failedNodeId" to error.failedNodeId.toString(),
            "failedNodeName" to error.failedNodeName,
            "failedNodeType" to error.failedNodeType,
            "errorType" to error.errorType.name,
            "message" to error.message,
            "totalRetryCount" to error.totalRetryCount,
            "timestamp" to error.timestamp.toString()
        )
    }
}
