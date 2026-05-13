package cranium.core.models.response.workflow

import cranium.core.models.workflow.node.WorkflowNode

/**
 * Response model for workflow node save operations.
 *
 * @property node The saved workflow node (null if save failed)
 * @property errors Validation or processing errors (null if successful)
 * @property created True if a new node was created, false if an existing node was updated
 */
data class SaveWorkflowNodeResponse(
    val node: WorkflowNode? = null,
    val errors: List<String>? = null,
    val created: Boolean = false,
)
