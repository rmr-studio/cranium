package cranium.core.models.request.block

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.models.block.operation.*
import java.time.ZonedDateTime
import java.util.*

data class StructuralOperationRequest(
    val id: UUID,
    val timestamp: ZonedDateTime,
    @get:Schema(
        oneOf = [
            AddBlockOperation::class,
            RemoveBlockOperation::class,
            MoveBlockOperation::class,
            UpdateBlockOperation::class,
            ReorderBlockOperation::class
        ],
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "ADD_BLOCK", schema = AddBlockOperation::class),
            DiscriminatorMapping(value = "REMOVE_BLOCK", schema = RemoveBlockOperation::class),
            DiscriminatorMapping(value = "MOVE_BLOCK", schema = MoveBlockOperation::class),
            DiscriminatorMapping(value = "UPDATE_BLOCK", schema = UpdateBlockOperation::class),
            DiscriminatorMapping(value = "REORDER_BLOCK", schema = ReorderBlockOperation::class)
        ]
    )
    @param:JsonProperty("data")
    val data: BlockOperation
)