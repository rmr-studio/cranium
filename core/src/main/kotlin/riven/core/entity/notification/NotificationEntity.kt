package cranium.core.entity.notification

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.enums.notification.NotificationReferenceType
import cranium.core.enums.notification.NotificationType
import cranium.core.models.notification.Notification
import cranium.core.models.notification.NotificationContent
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "notifications")
@SQLRestriction("deleted = false")
data class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: NotificationType,

    @Type(JsonBinaryType::class)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    val content: NotificationContent,

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type")
    val referenceType: NotificationReferenceType? = null,

    @Column(name = "reference_id", columnDefinition = "uuid")
    val referenceId: UUID? = null,

    @Column(name = "resolved", nullable = false)
    var resolved: Boolean = false,

    @Column(name = "resolved_at")
    var resolvedAt: ZonedDateTime? = null,

    @Column(name = "expires_at")
    val expiresAt: ZonedDateTime? = null,
) : AuditableSoftDeletableEntity() {

    fun toModel(): Notification {
        val id = requireNotNull(this.id) { "NotificationEntity ID cannot be null when converting to model" }
        return Notification(
            id = id,
            workspaceId = this.workspaceId,
            userId = this.userId,
            type = this.type,
            content = this.content,
            referenceType = this.referenceType,
            referenceId = this.referenceId,
            resolved = this.resolved,
            resolvedAt = this.resolvedAt,
            expiresAt = this.expiresAt,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            createdBy = this.createdBy,
            updatedBy = this.updatedBy,
        )
    }
}
