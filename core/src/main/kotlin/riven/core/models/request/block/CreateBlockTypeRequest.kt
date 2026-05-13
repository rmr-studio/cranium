package cranium.core.models.request.block

import cranium.core.enums.common.validation.ValidationScope
import cranium.core.models.block.BlockTypeSchema
import cranium.core.models.block.display.BlockDisplay
import java.util.*

data class CreateBlockTypeRequest(
    // The unique key for the block type.
    val key: String,
    // The name of the block type.
    val name: String,
    // An optional description of the block type.
    val description: String?,
    // The validation mode for the block type.
    val mode: ValidationScope = ValidationScope.STRICT,
    // The schema defining the structure of the block's data.
    val schema: BlockTypeSchema,
    // The display configuration for rendering the block.
    val display: BlockDisplay,
    // The ID of the workspace creating the block type.
    val workspaceId: UUID
)