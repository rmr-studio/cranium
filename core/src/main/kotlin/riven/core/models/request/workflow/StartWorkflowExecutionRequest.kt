package cranium.core.models.request.workflow

import java.util.UUID

/**
 * Request model for starting a workflow execution.
 *
 * @property workflowDefinitionId UUID of the workflow definition to execute
 * @property workspaceId UUID of the workspace context
 */
data class StartWorkflowExecutionRequest(
    val workflowDefinitionId: UUID,
    val workspaceId: UUID
)
