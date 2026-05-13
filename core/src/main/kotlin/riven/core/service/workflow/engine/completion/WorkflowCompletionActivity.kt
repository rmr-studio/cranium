package cranium.core.service.workflow.engine.completion

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.workflow.engine.error.WorkflowExecutionError
import java.util.*

/**
 * Temporal activity interface for recording workflow completion status.
 *
 * This activity is called at the end of workflow execution to:
 * - Update WorkflowExecutionEntity with final status (COMPLETED/FAILED)
 * - Update or remove the queue item
 * - Persist error details for failed workflows
 *
 * Separating completion handling from the dispatcher ensures:
 * - Queue only manages dispatch (PENDING → CLAIMED → DISPATCHED)
 * - Temporal owns all execution retries
 * - Final status is recorded reliably via activity retry
 *
 * @see WorkflowCompletionActivityImpl
 */
@ActivityInterface
interface WorkflowCompletionActivity {

    /**
     * Record the final completion status of a workflow execution.
     *
     * Called by WorkflowOrchestrationService at the end of execute().
     * Runs in its own transaction, independent of the dispatcher's ShedLock.
     *
     * This activity:
     * 1. Updates WorkflowExecutionEntity with status, completedAt, duration, error
     * 2. Updates queue item status (COMPLETED) or deletes it
     * 3. Logs completion for audit trail
     *
     * Retry behavior:
     * - Retries on transient DB errors (connection issues, deadlocks)
     * - Max 3 attempts with exponential backoff
     * - Failure here doesn't affect workflow result (already determined)
     *
     * @param executionId The workflow execution UUID
     * @param status Final status (COMPLETED or FAILED)
     * @param error Error details if status is FAILED, null otherwise
     */
    @ActivityMethod
    fun recordCompletion(
        executionId: UUID,
        status: WorkflowStatus,
        error: WorkflowExecutionError?
    )
}
