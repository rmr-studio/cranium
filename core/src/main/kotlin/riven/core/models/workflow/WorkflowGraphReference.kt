package cranium.core.models.workflow

import java.util.*

data class WorkflowGraphReference(
    val nodeIds: Set<UUID>,
    val edgeIds: Set<UUID>
)