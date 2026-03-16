package riven.core.entity.integration

import jakarta.persistence.*
import riven.core.entity.util.AuditableEntity
import riven.core.enums.integration.SyncStatus
import riven.core.models.integration.IntegrationSyncState
import java.util.*

/**
 * JPA entity tracking per-connection per-entity-type sync progress.
 *
 * Extends AuditableEntity for audit fields but does NOT implement SoftDeletable —
 * this is system-managed state that is updated in-place and deleted via CASCADE
 * when the parent connection or entity type is removed.
 */
@Entity
@Table(
    name = "integration_sync_state",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_sync_state_connection_entity_type",
            columnNames = ["integration_connection_id", "entity_type_id"]
        )
    ]
)
data class IntegrationSyncStateEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "integration_connection_id", nullable = false, columnDefinition = "uuid")
    val integrationConnectionId: UUID,

    @Column(name = "entity_type_id", nullable = false, columnDefinition = "uuid")
    val entityTypeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: SyncStatus = SyncStatus.PENDING,

    @Column(name = "last_cursor")
    var lastCursor: String? = null,

    @Column(name = "consecutive_failure_count", nullable = false)
    var consecutiveFailureCount: Int = 0,

    @Column(name = "last_error_message")
    var lastErrorMessage: String? = null,

    @Column(name = "last_records_synced")
    var lastRecordsSynced: Int? = null,

    @Column(name = "last_records_failed")
    var lastRecordsFailed: Int? = null
) : AuditableEntity() {

    fun toModel() = IntegrationSyncState(
        id = requireNotNull(id) { "IntegrationSyncStateEntity.id must not be null when mapping to model" },
        integrationConnectionId = integrationConnectionId,
        entityTypeId = entityTypeId,
        status = status,
        lastCursor = lastCursor,
        consecutiveFailureCount = consecutiveFailureCount,
        lastErrorMessage = lastErrorMessage,
        lastRecordsSynced = lastRecordsSynced,
        lastRecordsFailed = lastRecordsFailed
    )
}
