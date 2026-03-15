package riven.core.service.websocket

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import riven.core.enums.websocket.WebSocketChannel
import riven.core.models.websocket.WebSocketMessage
import riven.core.models.websocket.WorkspaceEvent

/**
 * Listens for domain events published via ApplicationEventPublisher and
 * forwards them to the appropriate STOMP topic after transaction commit.
 *
 * This is the single point where domain events become WebSocket messages.
 * Services publish events without knowing about WebSocket infrastructure.
 */
@Component
class WebSocketEventListener(
    private val messagingTemplate: SimpMessagingTemplate,
    private val logger: KLogger,
) {

    /** Forwards workspace-scoped domain events to the appropriate STOMP topic after transaction commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onWorkspaceEvent(event: WorkspaceEvent) {
        val topic = WebSocketChannel.topicPath(event.workspaceId, event.channel)
        val message = WebSocketMessage.from(event)

        logger.debug {
            "Broadcasting ${event.channel}:${event.operation} to $topic " +
                "(entityId=${event.entityId}, userId=${event.userId})"
        }

        messagingTemplate.convertAndSend(topic, message)
    }
}
