package cranium.core.models.workflow.engine.execution

import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.common.json.JsonValue
import cranium.core.models.workflow.node.WorkflowNode
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

data class WorkflowExecutionNodeRecord(
    override val id: UUID,
    override val workspaceId: UUID,
    val executionId: UUID,
    val node: WorkflowNode? = null,

    val sequenceIndex: Int,

    override val status: WorkflowStatus,
    override val startedAt: ZonedDateTime,
    override val completedAt: ZonedDateTime? = null,
    override val duration: Duration? = null,
    val attempt: Int,

    override val input: JsonValue,
    override val output: JsonValue,
    override val error: JsonValue

) : ExecutionRecord