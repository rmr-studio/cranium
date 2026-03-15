package riven.core.service.util.factory.workflow

import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.workflow.ExecutionJobType
import riven.core.enums.workflow.ExecutionQueueStatus
import java.time.ZonedDateTime
import java.util.*

/**
 * Factory for creating ExecutionQueueEntity test data.
 */
object ExecutionQueueFactory {

    /**
     * Create an ExecutionQueueEntity for a WORKFLOW_EXECUTION job.
     */
    fun createWorkflowExecutionJob(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        workflowDefinitionId: UUID = UUID.randomUUID(),
        executionId: UUID? = null,
        status: ExecutionQueueStatus = ExecutionQueueStatus.PENDING,
        createdAt: ZonedDateTime = ZonedDateTime.now(),
        claimedAt: ZonedDateTime? = null,
        dispatchedAt: ZonedDateTime? = null,
        attemptCount: Int = 0,
        lastError: String? = null
    ): ExecutionQueueEntity = ExecutionQueueEntity(
        id = id,
        workspaceId = workspaceId,
        jobType = ExecutionJobType.WORKFLOW_EXECUTION,
        entityId = null,
        workflowDefinitionId = workflowDefinitionId,
        executionId = executionId,
        status = status,
        createdAt = createdAt,
        claimedAt = claimedAt,
        dispatchedAt = dispatchedAt,
        attemptCount = attemptCount,
        lastError = lastError
    )

    /**
     * Create an ExecutionQueueEntity for an IDENTITY_MATCH job.
     */
    fun createIdentityMatchJob(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        entityId: UUID = UUID.randomUUID(),
        status: ExecutionQueueStatus = ExecutionQueueStatus.PENDING,
        createdAt: ZonedDateTime = ZonedDateTime.now(),
        claimedAt: ZonedDateTime? = null,
        attemptCount: Int = 0,
        lastError: String? = null
    ): ExecutionQueueEntity = ExecutionQueueEntity(
        id = id,
        workspaceId = workspaceId,
        jobType = ExecutionJobType.IDENTITY_MATCH,
        entityId = entityId,
        workflowDefinitionId = null,
        executionId = null,
        status = status,
        createdAt = createdAt,
        claimedAt = claimedAt,
        dispatchedAt = null,
        attemptCount = attemptCount,
        lastError = lastError
    )
}
