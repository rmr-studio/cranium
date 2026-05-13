package cranium.core.models.request.notification

import cranium.core.enums.notification.NotificationReferenceType
import cranium.core.enums.notification.NotificationType
import cranium.core.models.notification.NotificationContent
import java.time.ZonedDateTime
import java.util.UUID

data class CreateNotificationRequest(
    val workspaceId: UUID,
    val userId: UUID? = null,
    val type: NotificationType,
    val content: NotificationContent,
    val referenceType: NotificationReferenceType? = null,
    val referenceId: UUID? = null,
    val expiresAt: ZonedDateTime? = null,
)
