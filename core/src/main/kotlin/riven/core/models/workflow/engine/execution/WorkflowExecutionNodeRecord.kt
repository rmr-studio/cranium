package riven.core.models.workflow.engine.execution

import riven.core.enums.workflow.WorkflowStatus
import riven.core.models.common.json.JsonValue
import riven.core.models.workflow.node.WorkflowNode
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