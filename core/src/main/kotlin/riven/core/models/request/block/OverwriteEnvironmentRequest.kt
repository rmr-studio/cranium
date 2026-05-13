package cranium.core.models.request.block

import cranium.core.models.block.BlockEnvironment
import java.util.*

data class OverwriteEnvironmentRequest(
    val layoutId: UUID,
    val workspaceId: UUID,
    val version: Int,
    val environment: BlockEnvironment,
)