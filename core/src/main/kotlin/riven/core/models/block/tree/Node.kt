package cranium.core.models.block.tree

import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.ValueDeserializer
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.deserializer.NodeDeserializer
import cranium.core.enums.block.node.NodeType
import cranium.core.models.block.Block

@Schema(hidden = true)
@JsonDeserialize(using = NodeDeserializer::class)
sealed interface Node {
    val type: NodeType
    val block: Block
    val warnings: List<String>
}

@Schema(
    name = "ContentNode",
    description = "Content node containing a block with optional children"
)
@JsonDeserialize(using = tools.jackson.databind.ValueDeserializer.None::class)
data class ContentNode(
    override val block: Block,
    override val warnings: List<String> = emptyList(),
    val children: List<Node>? = null,
) : Node {
    override val type: NodeType = NodeType.CONTENT
}


@Schema(
    name = "ReferenceNode",
    description = "Reference node containing a block with entity or block tree references"
)
@JsonDeserialize(using = tools.jackson.databind.ValueDeserializer.None::class)
data class ReferenceNode(
    override val block: Block,
    override val warnings: List<String> = emptyList(),
    @field:Schema(
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "ENTITY", schema = EntityReference::class),
            DiscriminatorMapping(value = "BLOCK", schema = BlockTreeReference::class),
        ],
        oneOf = [EntityReference::class, BlockTreeReference::class]
    )
    val reference: ReferencePayload
) : Node {
    override val type: NodeType = NodeType.REFERENCE
}