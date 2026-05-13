package cranium.core.models.response.workflow

import cranium.core.models.workflow.WorkflowDefinition

/**
 * Response model for workflow definition save operations.
 *
 * @property definition The saved workflow definition (null if save failed)
 * @property errors Validation or processing errors (null if successful)
 * @property created True if a new definition was created, false if an existing definition was updated
 */
data class SaveWorkflowDefinitionResponse(
    val definition: WorkflowDefinition? = null,
    val errors: List<String>? = null,
    val created: Boolean = false,
)
