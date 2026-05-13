package cranium.core.entity.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "notification_reads",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_notification_reads_user_notification",
            columnNames = ["user_id", "notification_id"]
        )
    ]
)
data class NotificationReadEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "notification_id", nullable = false)
    val notificationId: UUID,

    @Column(name = "read_at", nullable = false)
    val readAt: ZonedDateTime = ZonedDateTime.now(),
)
