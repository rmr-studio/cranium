package riven.core.repository.workflow

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.workflow.ExecutionJobType
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
     * Claim pending execution requests for processing, filtered by job type.
     *
     * Uses SKIP LOCKED to allow concurrent consumers:
     * - Claimed rows are locked but other PENDING rows remain available
     * - Prevents duplicate processing across instances
     * - Non-blocking for unclaimed rows
     *
     * @param jobType The [ExecutionJobType] to claim
     * @param batchSize Maximum items to claim
     * @return List of claimed entities (caller must update status to CLAIMED)
     */
    @Query(
        """
        SELECT * FROM execution_queue
        WHERE status = 'PENDING'
        AND job_type = CAST(:jobType AS VARCHAR)
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun claimPendingByJobType(
        @Param("jobType") jobType: ExecutionJobType,
        @Param("batchSize") batchSize: Int
    ): List<ExecutionQueueEntity>

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
     * Find stale claimed items for recovery, filtered by job type.
     *
     * Items claimed but not dispatched within timeout should be reclaimed.
     * Used by recovery jobs to prevent stuck items.
     *
     * @param jobType The [ExecutionJobType] to filter
     * @param minutesAgo Threshold in minutes (claimed_at older than this)
     * @return Stale claimed items
     */
    @Query(
        """
        SELECT * FROM execution_queue
        WHERE status = 'CLAIMED'
        AND job_type = CAST(:jobType AS VARCHAR)
        AND claimed_at < (CURRENT_TIMESTAMP - INTERVAL '1 minute' * :minutesAgo)
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findStaleClaimedByJobType(
        @Param("jobType") jobType: ExecutionJobType,
        @Param("minutesAgo") minutesAgo: Int
    ): List<ExecutionQueueEntity>

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
     * Bulk-enqueue ENRICHMENT items for every non-INTEGRATION, non-deleted entity of a given type
     * in a workspace. Used by manifest reconciliation to invalidate snapshots after a schema change.
     *
     * Single `INSERT ... SELECT` to avoid N+1 at high entity-type cardinality. The partial unique
     * index `uq_execution_queue_pending_identity_match` deduplicates against existing PENDING rows
     * via `ON CONFLICT DO NOTHING`, so reissuing the call on an already-queued workspace is a no-op.
     *
     * @return Count of rows actually inserted (excludes skipped duplicates).
     */
    @Modifying
    @Query(
        """
        INSERT INTO execution_queue (workspace_id, entity_id, job_type, status)
        SELECT e.workspace_id, e.id, 'ENRICHMENT', 'PENDING'
        FROM entities e
        WHERE e.type_id = :entityTypeId
          AND e.workspace_id = :workspaceId
          AND e.deleted = false
          AND e.source_type <> 'INTEGRATION'
        ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun enqueueEnrichmentByEntityType(
        @Param("entityTypeId") entityTypeId: UUID,
        @Param("workspaceId") workspaceId: UUID,
    ): Int

    /**
     * Enqueue ENRICHMENT items for every entity in [workspaceId] whose persisted
     * connotation snapshot's `metadata.<metadataKey>.analysisVersion` does NOT match
     * [currentVersion].
     *
     * `IS DISTINCT FROM` covers both "different version" and "null version stamp" without
     * special-casing nulls. Excludes entities that don't yet have a connotation snapshot row
     * (those should be enqueued via the regular [enqueueEnrichmentByEntityType] path).
     *
     * The [metadataKey] parameter is the JSON key used inside `connotation_metadata.metadata` —
     * the persisted snapshot shape uses uppercase metadata-type names (e.g. `"SENTIMENT"`,
     * `"RELATIONAL"`, `"STRUCTURAL"`) per the `@JsonProperty` declarations on
     * [riven.core.models.connotation.EntityMetadata]. Callers must pass the matching
     * uppercase key — passing `"sentiment"` will match zero rows.
     *
     * Skips soft-deleted entities. Idempotent via `ON CONFLICT DO NOTHING` against the
     * partial unique index `uq_execution_queue_pending_identity_match` — running twice
     * inserts no duplicates while a prior PENDING row still exists.
     *
     * @param metadataKey JSONB key of the metadata category to compare (e.g. `"SENTIMENT"`).
     * @param currentVersion Active analysis version string from configuration. Rows whose
     *   persisted version differs from this (including null) are enqueued.
     * @param workspaceId Workspace to scope the enqueue to.
     * @return Count of rows actually inserted (excludes skipped duplicates and rows already
     *   matching [currentVersion]).
     */
    @Modifying
    @Query(
        """
        INSERT INTO execution_queue (workspace_id, entity_id, job_type, status)
        SELECT ec.workspace_id, ec.entity_id, 'ENRICHMENT', 'PENDING'
        FROM entity_connotation ec
        JOIN entities e ON e.id = ec.entity_id AND e.deleted = false
        WHERE ec.workspace_id = :workspaceId
          AND (ec.connotation_metadata -> 'metadata' -> :metadataKey ->> 'analysisVersion') IS DISTINCT FROM :currentVersion
        ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun enqueueByMetadataVersionMismatch(
        @Param("metadataKey") metadataKey: String,
        @Param("currentVersion") currentVersion: String,
        @Param("workspaceId") workspaceId: UUID,
    ): Int
}
