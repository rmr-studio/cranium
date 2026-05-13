package cranium.core.models.block.operation

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import cranium.core.enums.block.request.BlockOperationType
import java.util.*

@JsonTypeName("MOVE_BLOCK")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class MoveBlockOperation(
    override val blockId: UUID,
    val fromParentId: UUID? = null,
    val toParentId: UUID? = null,
) : BlockOperation {
    override val type: BlockOperationType = BlockOperationType.MOVE_BLOCK
}