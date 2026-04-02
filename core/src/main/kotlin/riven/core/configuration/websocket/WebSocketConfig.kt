package riven.core.configuration.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import riven.core.configuration.properties.SecurityConfigurationProperties
import riven.core.configuration.properties.WebSocketConfigurationProperties

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val wsProperties: WebSocketConfigurationProperties,
    private val securityProperties: SecurityConfigurationProperties,
    private val webSocketSecurityInterceptor: WebSocketSecurityInterceptor,
) : WebSocketMessageBrokerConfigurer {

    private fun heartbeatScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("ws-heartbeat-")
            initialize()
        }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        val origins = wsProperties.allowedOrigins.ifEmpty { securityProperties.allowedOrigins }
        registry.addEndpoint(wsProperties.endpoint)
            .setAllowedOrigins(*origins.toTypedArray())
            .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(longArrayOf(wsProperties.serverHeartbeatMs, wsProperties.clientHeartbeatMs))
            .setTaskScheduler(heartbeatScheduler())
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        require(wsProperties.sendTimeoutMs in 0..Int.MAX_VALUE) {
            "riven.websocket.send-timeout-ms (${wsProperties.sendTimeoutMs}) must be between 0 and ${Int.MAX_VALUE}"
        }
        registration
            .setMessageSizeLimit(wsProperties.maxMessageSizeBytes)
            .setSendBufferSizeLimit(wsProperties.sendBufferSizeBytes)
            .setSendTimeLimit(wsProperties.sendTimeoutMs.toInt())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(webSocketSecurityInterceptor)
    }
}
