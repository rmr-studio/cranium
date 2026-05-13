package cranium.core.models.request.workflow

import cranium.core.models.workflow.node.config.WorkflowNodeConfig

/**
 * Request model for creating a new workflow node.
 *
 * @property key Unique identifier for the node within the workspace
 * @property name Human-readable display name
 * @property description Optional description of the node's purpose
 * @property config Polymorphic node configuration (ActionConfig, ControlConfig, etc.)
 */
data class CreateWorkflowNodeRequest(
    val key: String,
    val name: String,
    val description: String? = null,
    val config: WorkflowNodeConfig
)
