package cranium.core.models.response.block.internal

import cranium.core.entity.block.BlockChildEntity
import java.util.*

data class CascadeRemovalResult(
    val blocksToDelete: Set<UUID>,
    val childEntitiesToDelete: List<BlockChildEntity>
)
