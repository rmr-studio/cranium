package riven.core.repository.workflow

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.workflow.ExecutionQueueStatus
import java.util.UUID

/**
 * Repository for execution queue persistence with concurrent-safe claiming.
 *
 * Uses PostgreSQL FOR UPDATE SKIP LOCKED for safe concurrent queue consumption.
 * Multiple dispatcher instances can claim items without blocking each other.
 */
@Repository
interface ExecutionQueueRepository : JpaRepository<ExecutionQueueEntity, UUID> {

    /**
     * Claim pending execution requests for processing.
     *
     * Uses SKIP LOCKED to allow concurrent consumers:
     * - Claimed rows are locked but other PENDING rows remain available
     * - Prevents duplicate processing across instances
     * - Non-blocking for unclaimed rows
     *
     * @param batchSize Maximum items to claim
     * @return List of claimed entities (caller must update status to CLAIMED)
     */
    @Query(
        """
        SELECT * FROM execution_queue
        WHERE status = 'PENDING'
        AND job_type = 'WORKFLOW_EXECUTION'
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun claimPendingExecutions(@Param("batchSize") batchSize: Int): List<ExecutionQueueEntity>

    /**
     * Find queue items by workspace and status.
     *
     * @param workspaceId Workspace to filter
     * @param status Status to filter
     * @return Queue items ordered by creation time
     */
    fun findByWorkspaceIdAndStatusOrderByCreatedAtAsc(
        workspaceId: UUID,
        status: ExecutionQueueStatus
    ): List<ExecutionQueueEntity>

    /**
     * Count pending items for a workspace.
     *
     * @param workspaceId Workspace to count
     * @return Number of pending queue items
     */
    fun countByWorkspaceIdAndStatus(workspaceId: UUID, status: ExecutionQueueStatus): Int

    /**
     * Find stale claimed items (for recovery after crashes).
     *
     * Items claimed but not dispatched within timeout should be reclaimed.
     * Used by recovery job to prevent stuck items.
     *
     * @param minutesAgo Threshold in minutes (claimed_at older than this)
     * @return Stale claimed items
     */
    @Query(
        """
        SELECT * FROM execution_queue
        WHERE status = 'CLAIMED'
        AND job_type = 'WORKFLOW_EXECUTION'
        AND claimed_at < (CURRENT_TIMESTAMP - INTERVAL '1 minute' * :minutesAgo)
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findStaleClaimedItems(@Param("minutesAgo") minutesAgo: Int): List<ExecutionQueueEntity>

    /**
     * Find queue item by execution ID.
     *
     * Used by WorkflowCompletionActivity to update queue status after workflow completes.
     * No locking needed - targets specific record by unique execution_id.
     *
     * @param executionId The workflow execution UUID
     * @return Queue item if found, null otherwise
     */
    fun findByExecutionId(executionId: UUID): ExecutionQueueEntity?

    /**
     * Claim pending IDENTITY_MATCH jobs for dispatch.
     *
     * Mirrors [claimPendingExecutions] but filters exclusively on IDENTITY_MATCH job type.
     * Uses SKIP LOCKED so multiple dispatcher instances do not compete for the same rows.
     *
     * @param batchSize Maximum items to claim in one poll cycle.
     * @return Claimed entities — caller must transition status to CLAIMED.
     */
    @Query(
        """
        SELECT * FROM execution_queue
        WHERE status = 'PENDING'
        AND job_type = 'IDENTITY_MATCH'
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun claimPendingIdentityMatchJobs(@Param("batchSize") batchSize: Int): List<ExecutionQueueEntity>

    /**
     * Find stale CLAIMED IDENTITY_MATCH items for recovery.
     *
     * Items that were claimed but not dispatched within the timeout window are candidates
     * for being reset to PENDING. Mirrors [findStaleClaimedItems] for IDENTITY_MATCH jobs.
     *
     * @param minutesAgo Threshold in minutes — items claimed more than this many minutes ago.
     * @return Stale claimed items eligible for retry.
     */
    @Query(
        """
        SELECT * FROM execution_queue
        WHERE status = 'CLAIMED'
        AND job_type = 'IDENTITY_MATCH'
        AND claimed_at < (CURRENT_TIMESTAMP - INTERVAL '1 minute' * :minutesAgo)
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findStaleClaimedIdentityMatchItems(@Param("minutesAgo") minutesAgo: Int): List<ExecutionQueueEntity>
}
