package cranium.core.models.response.notification

import cranium.core.models.notification.NotificationInboxItem

data class NotificationInboxResponse(
    val notifications: List<NotificationInboxItem>,
    val nextCursor: String?,
    val unreadCount: Long,
)
