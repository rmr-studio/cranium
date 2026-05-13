package cranium.core.models.request.workflow

import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType

/**
 * Request model for updating workflow definition metadata.
 * All fields are optional - only provided fields will be updated.
 */
data class UpdateWorkflowDefinitionRequest(
    val name: String? = null,
    val description: String? = null,
    val iconColour: IconColour? = null,
    val iconType: IconType? = null,
    val tags: List<String>? = null
)
