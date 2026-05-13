package cranium.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cranium.websocket")
data class WebSocketConfigurationProperties(
    /** STOMP endpoint path that clients connect to */
    val endpoint: String = "/ws",
    /** Allowed origins for WebSocket connections (defaults to security allowed-origins) */
    val allowedOrigins: List<String> = emptyList(),
    /** Server heartbeat interval in milliseconds (0 = disabled) */
    val serverHeartbeatMs: Long = 10000,
    /** Expected client heartbeat interval in milliseconds (0 = disabled) */
    val clientHeartbeatMs: Long = 10000,
    /** Maximum size of an inbound STOMP message in bytes */
    val maxMessageSizeBytes: Int = 65536,
    /** Send buffer size limit in bytes per WebSocket session */
    val sendBufferSizeBytes: Int = 524288,
    /** Send timeout in milliseconds */
    val sendTimeoutMs: Long = 15000,
)
