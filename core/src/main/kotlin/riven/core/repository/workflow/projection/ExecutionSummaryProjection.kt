package cranium.core.repository.workflow.projection

import cranium.core.entity.workflow.WorkflowNodeEntity
import cranium.core.entity.workflow.execution.WorkflowExecutionEntity
import cranium.core.entity.workflow.execution.WorkflowExecutionNodeEntity

/**
 * Projection for workflow execution summary containing the execution,
 * node executions, and their associated workflow nodes.
 *
 * This is constructed from a JOIN query that fetches all related data
 * in a single database call.
 */
data class ExecutionSummaryProjection(
    val execution: WorkflowExecutionEntity,
    val executionNode: WorkflowExecutionNodeEntity,
    val node: WorkflowNodeEntity?
)
