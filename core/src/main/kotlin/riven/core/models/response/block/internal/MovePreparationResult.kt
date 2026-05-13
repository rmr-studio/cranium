package cranium.core.models.response.block.internal

import cranium.core.entity.block.BlockChildEntity


data class MovePreparationResult(
    val childEntitiesToDelete: List<BlockChildEntity>,
    val childEntitiesToSave: List<BlockChildEntity>
)
