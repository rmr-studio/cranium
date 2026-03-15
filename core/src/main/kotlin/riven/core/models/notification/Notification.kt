package riven.core.models.notification

import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import java.time.ZonedDateTime
import java.util.UUID

data class Notification(
    val id: UUID,
    val workspaceId: UUID,
    val userId: UUID?,
    val type: NotificationType,
    val content: NotificationContent,
    val referenceType: NotificationReferenceType?,
    val referenceId: UUID?,
    val resolved: Boolean,
    val resolvedAt: ZonedDateTime?,
    val expiresAt: ZonedDateTime?,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
)
