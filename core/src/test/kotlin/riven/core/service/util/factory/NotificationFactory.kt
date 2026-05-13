package cranium.core.service.util.factory

import cranium.core.entity.notification.NotificationEntity
import cranium.core.entity.notification.NotificationReadEntity
import cranium.core.enums.notification.NotificationReferenceType
import cranium.core.enums.notification.NotificationType
import cranium.core.enums.notification.ReviewPriority
import cranium.core.enums.notification.SystemSeverity
import cranium.core.models.notification.NotificationContent
import java.time.ZonedDateTime
import java.util.UUID

object NotificationFactory {

    val DEFAULT_NOTIFICATION_ID: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")

    fun createEntity(
        id: UUID? = DEFAULT_NOTIFICATION_ID,
        workspaceId: UUID,
        userId: UUID? = null,
        type: NotificationType = NotificationType.INFORMATION,
        content: NotificationContent = informationContent(),
        referenceType: NotificationReferenceType? = null,
        referenceId: UUID? = null,
        resolved: Boolean = false,
        resolvedAt: ZonedDateTime? = null,
        expiresAt: ZonedDateTime? = null,
    ): NotificationEntity = NotificationEntity(
        id = id,
        workspaceId = workspaceId,
        userId = userId,
        type = type,
        content = content,
        referenceType = referenceType,
        referenceId = referenceId,
        resolved = resolved,
        resolvedAt = resolvedAt,
        expiresAt = expiresAt,
    )

    val DEFAULT_READ_ID: UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901")
    val DEFAULT_READ_AT: ZonedDateTime = ZonedDateTime.parse("2026-01-15T10:30:00Z")

    fun createReadEntity(
        id: UUID? = DEFAULT_READ_ID,
        userId: UUID,
        notificationId: UUID,
        readAt: ZonedDateTime = DEFAULT_READ_AT,
    ): NotificationReadEntity = NotificationReadEntity(
        id = id,
        userId = userId,
        notificationId = notificationId,
        readAt = readAt,
    )

    fun informationContent(
        title: String = "Test Notification",
        message: String = "This is a test notification.",
        sourceLabel: String? = null,
    ): NotificationContent.Information = NotificationContent.Information(
        title = title,
        message = message,
        sourceLabel = sourceLabel,
    )

    fun reviewRequestContent(
        title: String = "Review Required",
        message: String = "An item requires your review.",
        contextSummary: String? = "Entity A may be linked to Entity B",
        priority: ReviewPriority = ReviewPriority.NORMAL,
    ): NotificationContent.ReviewRequest = NotificationContent.ReviewRequest(
        title = title,
        message = message,
        contextSummary = contextSummary,
        priority = priority,
    )

    fun systemContent(
        title: String = "System Notice",
        message: String = "A system event has occurred.",
        severity: SystemSeverity = SystemSeverity.INFO,
    ): NotificationContent.System = NotificationContent.System(
        title = title,
        message = message,
        severity = severity,
    )
}
