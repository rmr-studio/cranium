package cranium.core.models.block

import cranium.core.models.block.tree.BlockTree
import cranium.core.models.block.tree.BlockTreeLayout

data class BlockEnvironment(
    val layout: BlockTreeLayout,
    val trees: List<BlockTree>,
)