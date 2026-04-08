package riven.core.entity.enrichment

import jakarta.persistence.*
import riven.core.enums.enrichment.EnrichmentQueuePriority
import riven.core.enums.enrichment.EnrichmentQueueStatus
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for the enrichment_queue table.
 *
 * Represents an enrichment work item waiting to be dispatched.
 * Queue items progress through: PENDING -> CLAIMED -> DISPATCHED -> COMPLETED (or FAILED)
 *
 * System-managed: no AuditableEntity, no SoftDeletable.
 * Uses PostgreSQL FOR UPDATE SKIP LOCKED for concurrent-safe claiming.
 */
@Entity
@Table(
    name = "enrichment_queue",
    indexes = [
        Index(name = "idx_enrichment_queue_workspace_status", columnList = "workspace_id, status")
    ]
)
data class EnrichmentQueueEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    val entityId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    val priority: EnrichmentQueuePriority = EnrichmentQueuePriority.NORMAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: EnrichmentQueueStatus = EnrichmentQueueStatus.PENDING,

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "claimed_at", columnDefinition = "timestamptz")
    var claimedAt: ZonedDateTime? = null,

    @Column(name = "dispatched_at", columnDefinition = "timestamptz")
    var dispatchedAt: ZonedDateTime? = null,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "last_error", columnDefinition = "text")
    var lastError: String? = null
)
