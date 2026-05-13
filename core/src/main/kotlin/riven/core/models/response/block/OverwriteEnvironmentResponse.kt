package cranium.core.models.response.block

import cranium.core.models.block.BlockEnvironment

data class OverwriteEnvironmentResponse(
    val success: Boolean,
    val environment: BlockEnvironment
)