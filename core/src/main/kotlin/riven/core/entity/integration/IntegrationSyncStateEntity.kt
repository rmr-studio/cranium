package riven.core.entity.integration

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import org.hibernate.annotations.UpdateTimestamp
import riven.core.enums.integration.SyncStatus
import riven.core.models.integration.IntegrationSyncState
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity tracking per-connection per-entity-type sync progress.
 *
 * System-managed state — does NOT extend AuditableEntity or implement SoftDeletable.
 * Updated in-place and deleted via CASCADE when the parent connection or entity type is removed.
 * Manages its own createdAt/updatedAt timestamps without user audit fields (createdBy/updatedBy).
 */
@Entity
@Table(
    name = "integration_sync_state",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_sync_state_connection_entity_type_key",
            columnNames = ["integration_connection_id", "entity_type_id", "sync_key"]
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
    var lastRecordsFailed: Int? = null,

    @Column(name = "last_pipeline_step", length = 50)
    var lastPipelineStep: String? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "projection_result", columnDefinition = "jsonb")
    var projectionResult: Map<String, Any>? = null,

    @Column(name = "sync_key", length = 100)
    val syncKey: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {

    fun toModel() = IntegrationSyncState(
        id = requireNotNull(id) { "IntegrationSyncStateEntity.id must not be null when mapping to model" },
        integrationConnectionId = integrationConnectionId,
        entityTypeId = entityTypeId,
        status = status,
        lastCursor = lastCursor,
        consecutiveFailureCount = consecutiveFailureCount,
        lastErrorMessage = lastErrorMessage,
        lastRecordsSynced = lastRecordsSynced,
        lastRecordsFailed = lastRecordsFailed,
        lastPipelineStep = lastPipelineStep,
        projectionResult = projectionResult
    )
}
