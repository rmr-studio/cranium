package cranium.core.models.block.operation

import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.deserializer.BlockOperationDeserializer
import cranium.core.enums.block.request.BlockOperationType
import java.util.*

@Schema(hidden = true)
@JsonDeserialize(using = BlockOperationDeserializer::class)
sealed interface BlockOperation {
    val type: BlockOperationType
    val blockId: UUID
}