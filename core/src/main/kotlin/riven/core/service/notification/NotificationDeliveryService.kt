package cranium.core.service.notification

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import cranium.core.enums.notification.NotificationReferenceType
import cranium.core.enums.notification.NotificationType
import cranium.core.models.notification.Notification
import cranium.core.models.notification.NotificationContent
import cranium.core.models.request.notification.CreateNotificationRequest
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Translates domain events into notifications and manages resolution.
 *
 * Event listener methods for specific domain events (identity resolution,
 * workflow human-in-the-loop) will be added as those features are implemented.
 */
@Service
class NotificationDeliveryService(
    private val notificationService: NotificationService,
    private val logger: KLogger,
) {

    // ------ Notification Creation ------

    /** Creates a workspace-wide notification visible to all members. */
    fun createForWorkspace(
        workspaceId: UUID,
        type: NotificationType,
        content: NotificationContent,
        referenceType: NotificationReferenceType? = null,
        referenceId: UUID? = null,
        expiresAt: ZonedDateTime? = null,
    ): Notification {
        return notificationService.createInternalNotification(
            CreateNotificationRequest(
                workspaceId = workspaceId,
                type = type,
                content = content,
                referenceType = referenceType,
                referenceId = referenceId,
                expiresAt = expiresAt,
            )
        )
    }

    /** Creates a notification targeted to a specific user within a workspace. */
    fun createForUser(
        workspaceId: UUID,
        userId: UUID,
        type: NotificationType,
        content: NotificationContent,
        referenceType: NotificationReferenceType? = null,
        referenceId: UUID? = null,
        expiresAt: ZonedDateTime? = null,
    ): Notification {
        return notificationService.createInternalNotification(
            CreateNotificationRequest(
                workspaceId = workspaceId,
                userId = userId,
                type = type,
                content = content,
                referenceType = referenceType,
                referenceId = referenceId,
                expiresAt = expiresAt,
            )
        )
    }

    // ------ Resolution ------

    /** Marks all unresolved notifications referencing the given entity as resolved. */
    fun resolveByReference(referenceType: NotificationReferenceType, referenceId: UUID) {
        notificationService.resolveByReference(referenceType, referenceId)
        logger.info { "Resolved notifications for $referenceType:$referenceId" }
    }
}
