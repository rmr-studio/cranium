package cranium.core.models.block.operation

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import cranium.core.enums.block.request.BlockOperationType
import java.util.*

@JsonTypeName("REORDER_BLOCK")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class ReorderBlockOperation(
    override val blockId: UUID,
    // The List node the current child block is posted under
    val parentId: UUID,
    val fromIndex: Int,
    val toIndex: Int
) : BlockOperation {
    override val type: BlockOperationType = BlockOperationType.REORDER_BLOCK
}