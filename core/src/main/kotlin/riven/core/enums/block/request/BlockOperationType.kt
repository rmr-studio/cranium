package cranium.core.enums.block.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    name = "BlockOperationType",
    description = "Enumeration of possible block operation types for requests.",
    enumAsRef = true,
)
enum class BlockOperationType {
    ADD_BLOCK,
    REMOVE_BLOCK,
    MOVE_BLOCK,
    UPDATE_BLOCK,
    REORDER_BLOCK,
}