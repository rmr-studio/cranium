package riven.core.models.websocket

import riven.core.enums.util.OperationType
import riven.core.enums.websocket.WebSocketChannel
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Envelope sent to WebSocket subscribers. Contains enough data
 * for feed/list views to update in-place without a REST roundtrip.
 * Detail views should refetch via REST when they receive this notification.
 */
data class WebSocketMessage(
    val channel: WebSocketChannel,
    val operation: OperationType,
    val workspaceId: UUID,
    val entityId: UUID?,
    val userId: UUID,
    val timestamp: ZonedDateTime,
    val summary: Map<String, Any?>,
) {
    companion object {
        fun from(event: WorkspaceEvent): WebSocketMessage = WebSocketMessage(
            channel = event.channel,
            operation = event.operation,
            workspaceId = event.workspaceId,
            entityId = event.entityId,
            userId = event.userId,
            timestamp = ZonedDateTime.now(),
            summary = event.summary,
        )
    }
}
