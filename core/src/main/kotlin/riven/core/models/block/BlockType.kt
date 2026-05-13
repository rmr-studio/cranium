package cranium.core.models.block

import cranium.core.enums.common.validation.ValidationScope
import cranium.core.models.block.display.BlockDisplay
import cranium.core.models.block.display.BlockTypeNesting
import cranium.core.models.common.validation.Schema
import java.time.ZonedDateTime
import java.util.*

data class BlockType(
    val id: UUID,
    val key: String,
    val version: Int,
    val name: String,
    val sourceId: UUID?,
    // Defines how this block type accepts nesting of other block types
    // Null implies no nesting allowed
    val nesting: BlockTypeNesting?,
    val description: String?,
    val workspaceId: UUID?,
    val deleted: Boolean,
    val strictness: ValidationScope,
    val system: Boolean,
    val schema: BlockTypeSchema,
    val display: BlockDisplay,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
)

typealias BlockTypeSchema = Schema<String>



