package cranium.core.models.workflow.engine.queue

import cranium.core.enums.workflow.ExecutionJobType
import cranium.core.enums.workflow.ExecutionQueueStatus
import java.time.ZonedDateTime
import java.util.UUID

data class ExecutionQueueRequest(
    val id: UUID,
    val workspaceId: UUID,
    val jobType: ExecutionJobType = ExecutionJobType.WORKFLOW_EXECUTION,
    val entityId: UUID? = null,
    val workflowDefinitionId: UUID?,
    val executionId: UUID?,
    val status: ExecutionQueueStatus,
    val createdAt: ZonedDateTime,
    val claimedAt: ZonedDateTime?,
    val dispatchedAt: ZonedDateTime?,
    val attemptCount: Int,
    val lastError: String?
)
