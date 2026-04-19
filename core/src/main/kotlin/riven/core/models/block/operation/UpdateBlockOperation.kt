package riven.core.models.block.operation

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.enums.block.request.BlockOperationType
import riven.core.models.block.tree.*
import java.util.*

@JsonTypeName("UPDATE_BLOCK")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class UpdateBlockOperation(
    override val blockId: UUID,

    @field:Schema(
        oneOf = [ContentNode::class, ReferenceNode::class],
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "entity_reference", schema = EntityReference::class),
            DiscriminatorMapping(value = "block_reference", schema = BlockTreeReference::class),
        ]
    )
    val updatedContent: Node
) : BlockOperation {
    override val type: BlockOperationType = BlockOperationType.UPDATE_BLOCK
}
