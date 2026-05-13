package cranium.core.models.workflow.engine.execution

import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.enums.workflow.WorkflowTriggerType
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

data class WorkflowExecutionRecord(
    override val id: UUID,
    override val workspaceId: UUID,
    val workflowDefinitionId: UUID,
    val workflowVersionId: UUID,
    
    override val status: WorkflowStatus,
    override val startedAt: ZonedDateTime,
    override val completedAt: ZonedDateTime? = null,
    override val duration: Duration? = null,

    val triggerType: WorkflowTriggerType,
    override val input: Any?,

    override val error: Any?,
    override val output: Any?
) : ExecutionRecord