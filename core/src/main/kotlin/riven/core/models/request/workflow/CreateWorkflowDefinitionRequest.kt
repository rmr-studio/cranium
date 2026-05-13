package cranium.core.models.request.workflow

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType

/**
 * Request model for creating a new workflow definition.
 */
data class CreateWorkflowDefinitionRequest(
    val name: String,
    val description: String? = null,
    val iconColour: IconColour = IconColour.NEUTRAL,
    val iconType: IconType = IconType.WORKFLOW,
    val tags: List<String> = emptyList()
)
