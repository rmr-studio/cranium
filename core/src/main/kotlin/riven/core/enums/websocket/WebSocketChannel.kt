package cranium.core.enums.websocket

import java.util.UUID

/**
 * Maps domain concerns to STOMP topic segments.
 * Topics follow the pattern: /topic/workspace/{workspaceId}/{channel}
 */
enum class WebSocketChannel(val topicSegment: String) {
    ENTITIES("entities"),
    BLOCKS("blocks"),
    WORKFLOWS("workflows"),
    NOTIFICATIONS("notifications"),
    WORKSPACE("workspace");

    companion object {
        /** Builds a fully-qualified STOMP topic path for a workspace channel. */
        fun topicPath(workspaceId: UUID, channel: WebSocketChannel): String =
            "/topic/workspace/$workspaceId/${channel.topicSegment}"
    }
}
