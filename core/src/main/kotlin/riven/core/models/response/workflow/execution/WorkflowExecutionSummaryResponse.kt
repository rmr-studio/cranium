package cranium.core.models.response.workflow.execution

import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.workflow.engine.execution.WorkflowExecutionNodeRecord
import cranium.core.models.workflow.engine.execution.WorkflowExecutionRecord

/**
 * Summary response for workflow execution queries.
 *
 * Contains the execution record and all node execution records.
 *
 * Error details are available in the error fields:
 * - execution.error: WorkflowExecutionError (when workflow fails)
 * - node.error: NodeExecutionError (when node fails, includes retry history)
 *
 * These error objects are stored as JSONB in the database and automatically
 * serialized to JSON in API responses.
 *
 * @property execution The workflow execution record with overall status and error
 * @property nodes List of node execution records with individual status and errors
 */
data class WorkflowExecutionSummaryResponse(
    val execution: WorkflowExecutionRecord,
    val nodes: List<WorkflowExecutionNodeRecord>
) {
    /**
     * Returns the first failed node's record, if any.
     *
     * Useful for quickly identifying which node caused the workflow to fail.
     * The error details are in failedNode.error (NodeExecutionError).
     */
    val failedNode: WorkflowExecutionNodeRecord?
        get() = nodes.firstOrNull { it.status == WorkflowStatus.FAILED }

    /**
     * Returns true if any node in the execution failed.
     */
    val hasErrors: Boolean
        get() = nodes.any { it.status == WorkflowStatus.FAILED }

    /**
     * Returns all failed nodes in execution order.
     *
     * In most cases there will be only one failed node (execution stops on failure),
     * but parallel execution could have multiple failures.
     */
    val failedNodes: List<WorkflowExecutionNodeRecord>
        get() = nodes.filter { it.status == WorkflowStatus.FAILED }
}