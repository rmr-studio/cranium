package riven.core.configuration.websocket

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import riven.core.configuration.auth.CustomAuthenticationTokenConverter
import java.util.UUID

/**
 * Intercepts inbound STOMP frames to enforce authentication and authorization:
 * - CONNECT: validates JWT token from the `Authorization` header and populates
 *   the STOMP session's authentication principal.
 * - SUBSCRIBE: extracts workspaceId from the topic path and verifies the
 *   authenticated user has access to that workspace.
 */
@Component
class WebSocketSecurityInterceptor(
    private val jwtDecoder: JwtDecoder,
    private val tokenConverter: CustomAuthenticationTokenConverter,
    private val logger: KLogger,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        when (accessor.command) {
            StompCommand.CONNECT -> handleConnect(accessor)
            StompCommand.SUBSCRIBE -> handleSubscribe(accessor)
            else -> {} // No auth checks for other commands
        }

        return message
    }

    /**
     * Validates the JWT token provided in the STOMP CONNECT frame's Authorization header.
     * On success, sets the authenticated principal on the STOMP session so subsequent
     * frames (SUBSCRIBE, SEND) can access the user's identity and authorities.
     */
    private fun handleConnect(accessor: StompHeaderAccessor) {
        val token = extractBearerToken(accessor)
            ?: throw AuthenticationCredentialsNotFoundException("Missing Authorization header on CONNECT")

        try {
            val jwt = jwtDecoder.decode(token)
            val authentication = tokenConverter.convert(jwt)
            accessor.user = authentication
        } catch (e: JwtException) {
            logger.warn { "WebSocket CONNECT rejected — invalid JWT: ${e.message}" }
            throw AuthenticationCredentialsNotFoundException("Invalid JWT token: ${e.message}")
        }
    }

    /**
     * Authorizes SUBSCRIBE requests by checking the topic path for a workspace ID
     * and verifying the authenticated user has a role in that workspace.
     *
     * Topic format: /topic/workspace/{workspaceId}/...
     */
    private fun handleSubscribe(accessor: StompHeaderAccessor) {
        val auth = accessor.user as? JwtAuthenticationToken
            ?: throw AuthenticationCredentialsNotFoundException("Not authenticated — CONNECT required before SUBSCRIBE")

        val destination = accessor.destination
            ?: throw IllegalArgumentException("SUBSCRIBE frame missing destination")

        val workspaceId = extractWorkspaceId(destination)
            ?: throw AccessDeniedException("Subscription to unknown destination '$destination' is not allowed")

        val hasAccess = auth.authorities.any { authority ->
            authority.authority.startsWith("ROLE_$workspaceId")
        }

        if (!hasAccess) {
            logger.warn { "WebSocket SUBSCRIBE rejected — user ${auth.name} lacks access to workspace $workspaceId" }
            throw AccessDeniedException("Access denied to workspace $workspaceId")
        }
    }

    private fun extractBearerToken(accessor: StompHeaderAccessor): String? {
        val authHeader = accessor.getFirstNativeHeader("Authorization") ?: return null
        return if (authHeader.startsWith("Bearer ", ignoreCase = true)) {
            authHeader.substring(7)
        } else {
            null
        }
    }

    companion object {
        private val WORKSPACE_TOPIC_PATTERN = Regex("^/topic/workspace/([0-9a-fA-F\\-]{36})(/.*)?$")

        fun extractWorkspaceId(destination: String): UUID? {
            return WORKSPACE_TOPIC_PATTERN.matchEntire(destination)
                ?.groupValues?.get(1)
                ?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
        }
    }
}
