package riven.core.models.response.notification

import riven.core.models.notification.NotificationInboxItem

data class NotificationInboxResponse(
    val notifications: List<NotificationInboxItem>,
    val nextCursor: String?,
    val unreadCount: Long,
)
