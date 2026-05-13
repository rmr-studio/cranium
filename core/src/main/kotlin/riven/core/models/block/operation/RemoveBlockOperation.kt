package cranium.core.models.block.operation

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import cranium.core.enums.block.request.BlockOperationType
import java.util.*

@JsonTypeName("REMOVE_BLOCK")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class RemoveBlockOperation(
    override val blockId: UUID,
    // Map of child block IDs to their respective parent block IDs
    val childrenIds: Map<UUID, UUID> = emptyMap<UUID, UUID>(),
    // Optional parent ID to help locate the block and remove existing references
    val parentId: UUID? = null
) : BlockOperation {
    override val type: BlockOperationType = BlockOperationType.REMOVE_BLOCK
}