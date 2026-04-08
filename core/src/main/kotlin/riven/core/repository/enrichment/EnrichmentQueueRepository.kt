package riven.core.repository.enrichment

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import riven.core.entity.enrichment.EnrichmentQueueEntity
import riven.core.enums.enrichment.EnrichmentQueueStatus
import java.util.*

/**
 * Repository for enrichment queue persistence with concurrent-safe claiming.
 *
 * Uses PostgreSQL FOR UPDATE SKIP LOCKED for safe concurrent queue consumption.
 */
@Repository
interface EnrichmentQueueRepository : JpaRepository<EnrichmentQueueEntity, UUID> {

    /**
     * Claim pending enrichment items for processing.
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
        SELECT * FROM enrichment_queue
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun claimPendingItems(@Param("batchSize") batchSize: Int): List<EnrichmentQueueEntity>

    /**
     * Find queue items by workspace and status.
     *
     * @param workspaceId Workspace to filter
     * @param status Status to filter
     * @return Queue items ordered by creation time
     */
    fun findByWorkspaceIdAndStatusOrderByCreatedAtAsc(
        workspaceId: UUID,
        status: EnrichmentQueueStatus
    ): List<EnrichmentQueueEntity>

    /**
     * Count items for a workspace by status.
     *
     * @param workspaceId Workspace to count
     * @param status Status to filter
     * @return Number of queue items matching criteria
     */
    fun countByWorkspaceIdAndStatus(workspaceId: UUID, status: EnrichmentQueueStatus): Int
}
