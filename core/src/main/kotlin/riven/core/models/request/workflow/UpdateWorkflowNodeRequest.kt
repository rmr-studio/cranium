package cranium.core.models.request.workflow

import cranium.core.models.workflow.node.config.WorkflowNodeConfig

/**
 * Request model for updating an existing workflow node.
 *
 * All fields are optional - only provided fields will be updated.
 * If config is provided, a new version of the node will be created
 * (immutable copy-on-write pattern).
 *
 * @property name Updated display name (optional)
 * @property description Updated description (optional)
 * @property config Updated configuration (optional - triggers new version creation)
 */
data class UpdateWorkflowNodeRequest(
    val name: String? = null,
    val description: String? = null,
    val config: WorkflowNodeConfig? = null
)
