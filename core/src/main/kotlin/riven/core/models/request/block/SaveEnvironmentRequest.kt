package cranium.core.models.request.block

import cranium.core.models.block.layout.TreeLayout
import java.util.*

data class SaveEnvironmentRequest(
    val layoutId: UUID,
    val workspaceId: UUID,
    val layout: TreeLayout,
    val version: Int,
    val operations: List<StructuralOperationRequest>,
)