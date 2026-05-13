package cranium.core.service.identity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.workflow.ExecutionQueueEntity
import cranium.core.enums.workflow.ExecutionJobType
import cranium.core.enums.workflow.ExecutionQueueStatus
import cranium.core.repository.workflow.ExecutionQueueRepository
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Enqueues IDENTITY_MATCH jobs into the execution queue.
 *
 * Deduplication is enforced at the database layer via the partial unique index
 * `uq_execution_queue_pending_identity_match` on `(workspace_id, entity_id, job_type) WHERE status='PENDING'`.
 * A [DataIntegrityViolationException] from a duplicate insert is caught and silently swallowed —
 * the entity already has a pending match job in the queue.
 */
@Service
class IdentityMatchQueueService(
    private val executionQueueRepository: ExecutionQueueRepository,
    private val logger: KLogger,
) {

    // ------ Public mutations ------
    // Activity logging is intentionally omitted for internal queue operations.
    // ExecutionQueueEntity is infrastructure, not user-visible data — enqueue/claim/dispatch
    // events are traced via debug logging, not the activity audit trail.

    /**
     * Enqueues an IDENTITY_MATCH job for [entityId] if one is not already pending.
     *
     * If the dedup partial unique index fires (a PENDING job for this entity already exists),
     * the resulting [DataIntegrityViolationException] is caught and the method returns silently.
     *
     * @param entityId Entity to match.
     * @param workspaceId Workspace the entity belongs to.
     */
    @Transactional
    fun enqueueIfNotPending(entityId: UUID, workspaceId: UUID) {
        try {
            val job = ExecutionQueueEntity(
                workspaceId = workspaceId,
                jobType = ExecutionJobType.IDENTITY_MATCH,
                entityId = entityId,
                status = ExecutionQueueStatus.PENDING,
                createdAt = ZonedDateTime.now(),
            )
            executionQueueRepository.save(job)
            logger.debug { "Enqueued IDENTITY_MATCH job for entity $entityId in workspace $workspaceId" }
        } catch (e: DataIntegrityViolationException) {
            val isDedup = e.message?.contains("uq_execution_queue_pending_identity_match") == true
                    || e.cause?.message?.contains("uq_execution_queue_pending_identity_match") == true
            if (isDedup) {
                // Dedup: a PENDING IDENTITY_MATCH job for this entity already exists.
                // The partial unique index (workspace_id, entity_id, job_type) WHERE status='PENDING'
                // prevents duplicate queue entries — no action needed.
                logger.debug { "Skipping duplicate IDENTITY_MATCH enqueue for entity $entityId (PENDING job already exists)" }
            } else {
                throw e
            }
        }
    }
}
