package cranium.core.models.workflow.engine

import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.common.json.JsonValue
import java.util.UUID

/**
 * Result returned from Temporal workflow execution.
 *
 * Contains the execution outcome and results from all executed nodes.
 *
 * @property executionId UUID of the workflow execution record
 * @property status Workflow execution status (COMPLETED, FAILED, etc.)
 * @property nodeResults List of individual node execution results
 */
data class WorkflowExecutionResult(
    val executionId: UUID,
    val status: WorkflowStatus,
    val nodeResults: List<NodeExecutionResult>
)

/**
 * Result from executing a single workflow node.
 *
 * @property nodeId UUID of the executed node
 * @property status Node execution status (COMPLETED, FAILED, SKIPPED)
 * @property output Result data from the node execution (can be entity ID, expression result, etc.)
 * @property error Error message if execution failed
 */
data class NodeExecutionResult(
    val nodeId: UUID,
    val status: WorkflowStatus,
    val output: JsonValue = null,
    val error: String? = null
)
