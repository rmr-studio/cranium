package cranium.core.models.request.workflow

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import java.util.*

/**
 * Request model for saving a workflow definition (create or update).
 *
 * If [id] is null, a new workflow definition is created.
 * If [id] is provided, the existing workflow definition is updated.
 *
 * For creates, [name] is required.
 * For updates, only provided (non-null) fields will be updated.
 *
 * @property id Optional workflow definition ID - if present, performs update; if null, creates new
 * @property name Human-readable name for the workflow
 * @property description Optional description of the workflow's purpose
 * @property iconColour Icon colour for display
 * @property iconType Icon type for display
 * @property tags Optional list of tags for categorization
 */
data class SaveWorkflowDefinitionRequest(
    val id: UUID? = null,
    val name: String,
    val description: String? = null,
    val iconColour: IconColour = IconColour.NEUTRAL,
    val iconType: IconType = IconType.WORKFLOW,
    val tags: List<String> = emptyList(),
)
