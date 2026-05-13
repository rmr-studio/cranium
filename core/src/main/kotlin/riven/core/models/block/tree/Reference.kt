package cranium.core.models.block.tree

import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.deserializer.ReferencePayloadDeserializer
import cranium.core.enums.block.node.BlockReferenceWarning
import cranium.core.enums.block.node.ReferenceType
import cranium.core.models.entity.Entity
import java.util.*


@Schema(hidden = true)
@JsonDeserialize(using = ReferencePayloadDeserializer::class)
sealed interface ReferencePayload {
    val type: ReferenceType
}

@Schema(
    name = "EntityReference",
    description = "Reference to one or more of an workspace's entities (e.g. teams, projects, clients)"
)
@JsonDeserialize(using = ValueDeserializer.None::class)
data class EntityReference(
    val reference: List<ReferenceItem<Entity>>? = null
) : ReferencePayload {
    override val type: ReferenceType = ReferenceType.ENTITY
}

@Schema(
    name = "BlockTreeReference",
    description = "Reference to another block tree"
)
@JsonDeserialize(using = ValueDeserializer.None::class)
data class BlockTreeReference(
    val reference: ReferenceItem<BlockTree>? = null
) : ReferencePayload {
    override val type: ReferenceType = ReferenceType.BLOCK
}


data class ReferenceItem<T>(
    val id: UUID,
    val path: String? = null,
    val orderIndex: Int? = null,
    val entity: T? = null,
    val warning: BlockReferenceWarning? = null
)
