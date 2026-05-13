package cranium.core.entity.activity

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.models.activity.ActivityLog
import cranium.core.models.common.json.JsonObject
import java.time.ZonedDateTime
import java.util.*

@Entity
@Table(
    name = "activity_logs",
    indexes = [
        Index(columnList = "user_id"),
        Index(columnList = "workspace_id"),
    ]
)
data class ActivityLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "activity")
    val activity: Activity,

    @Enumerated(EnumType.STRING)
    @Column(name = "operation")
    val operation: OperationType,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    val entityType: ApplicationEntityType,

    @Column(name = "entity_id", columnDefinition = "uuid")
    val entityId: UUID? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false)
    val details: JsonObject,

    @Column(name = "timestamp", nullable = false)
    val timestamp: ZonedDateTime = ZonedDateTime.now(),

    ) {
    fun toModel(): ActivityLog {
        val id = requireNotNull(this.id) { "ActivityLogEntity ID cannot be null when converting to model" }
        return ActivityLog(
            id = id,
            activity = this.activity,
            operation = this.operation,
            userId = this.userId,
            workspaceId = this.workspaceId,
            entityType = this.entityType,
            entityId = this.entityId,
            details = this.details,
            timestamp = this.timestamp,
        )
    }
}