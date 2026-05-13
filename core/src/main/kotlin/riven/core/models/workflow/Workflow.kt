package cranium.core.models.workflow

import java.util.*

data class Workflow(
    val id: UUID,
    val definition: WorkflowDefinition,
    val version: Int
)