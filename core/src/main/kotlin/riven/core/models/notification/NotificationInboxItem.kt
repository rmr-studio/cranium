package riven.core.models.notification

import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import java.time.ZonedDateTime
import java.util.UUID

data class NotificationInboxItem(
    val id: UUID,
    val type: NotificationType,
    val content: NotificationContent,
    val referenceType: NotificationReferenceType?,
    val referenceId: UUID?,
    val resolved: Boolean,
    val read: Boolean,
    val createdAt: ZonedDateTime,
)
