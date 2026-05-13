package cranium.core.models.workflow.engine.execution

import java.time.Duration
import java.util.*

data class NodeExecutionConfig(
    val timeout: Duration?,
    val retryCount: Int?,
    val continueOnFailure: Boolean?,
    val errorHandlerId: UUID,
)