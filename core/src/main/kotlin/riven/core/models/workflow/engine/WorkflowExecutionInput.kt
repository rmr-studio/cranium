package cranium.core.models.workflow.engine

import java.util.UUID

/**
 * Input data for Temporal workflow execution.
 *
 * This model is passed to the WorkflowExecutionWorkflow.execute() method and contains
 * all the data needed for workflow orchestration.
 *
 * @property workflowDefinitionId UUID of the workflow definition to execute
 * @property nodeIds Ordered list of workflow node IDs to execute (topological sort for v1)
 * @property workspaceId UUID of the workspace context
 */
data class WorkflowExecutionInput(
    val workflowDefinitionId: UUID,
    val nodeIds: List<UUID>,
    val workspaceId: UUID
)
