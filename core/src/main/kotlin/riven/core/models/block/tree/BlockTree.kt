package riven.core.models.block.tree

import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema


@JsonDeserialize(using = ValueDeserializer.None::class)
data class BlockTree(
    @field:Schema(
        oneOf = [ContentNode::class, ReferenceNode::class],
        discriminatorProperty = "type",
        discriminatorMapping = [
            DiscriminatorMapping(value = "CONTENT", schema = ContentNode::class),
            DiscriminatorMapping(value = "REFERENCE", schema = ReferenceNode::class),
        ]
    )
    val root: Node,
)



