package riven.core.configuration.websocket

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.GenericMessage
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import riven.core.configuration.auth.CustomAuthenticationTokenConverter
import java.time.Instant
import java.util.UUID

class WebSocketSecurityInterceptorTest {

    private val jwtDecoder: JwtDecoder = mock()
    private val tokenConverter: CustomAuthenticationTokenConverter = mock()
    private val logger: KLogger = mock()
    private val channel: MessageChannel = mock()
    private val interceptor = WebSocketSecurityInterceptor(jwtDecoder, tokenConverter, logger)

    private val testUserId = UUID.randomUUID().toString()
    private val testWorkspaceId = UUID.randomUUID()

    private fun buildJwt(): Jwt = Jwt(
        "test-token",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        mapOf("alg" to "HS256", "typ" to "JWT"),
        mapOf("sub" to testUserId, "email" to "test@example.com")
    )

    private fun buildAuthToken(workspaceIds: List<UUID> = listOf(testWorkspaceId)): JwtAuthenticationToken {
        val authorities = workspaceIds.map {
            SimpleGrantedAuthority("ROLE_${it}_ADMIN")
        }
        return JwtAuthenticationToken(buildJwt(), authorities, testUserId)
    }

    private fun buildConnectMessage(token: String?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        accessor.setLeaveMutable(true)
        token?.let { accessor.addNativeHeader("Authorization", "Bearer $it") }
        return GenericMessage(ByteArray(0), accessor.messageHeaders)
    }

    private fun buildSubscribeMessage(
        destination: String,
        auth: JwtAuthenticationToken? = null
    ): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.setLeaveMutable(true)
        accessor.destination = destination
        auth?.let { accessor.user = it }
        return GenericMessage(ByteArray(0), accessor.messageHeaders)
    }

    // ------ CONNECT Tests ------

    @Test
    fun `CONNECT with valid JWT sets authentication on session`() {
        val jwt = buildJwt()
        val authToken = buildAuthToken()

        whenever(jwtDecoder.decode("valid-token")).thenReturn(jwt)
        whenever(tokenConverter.convert(jwt)).thenReturn(authToken)

        val message = buildConnectMessage("valid-token")
        val result = interceptor.preSend(message, channel)

        assertNotNull(result)
        verify(jwtDecoder).decode("valid-token")
        verify(tokenConverter).convert(jwt)

        val accessor = StompHeaderAccessor.wrap(result!!)
        assertEquals(authToken, accessor.user, "Session principal should be set to the authenticated token")
    }

    @Test
    fun `CONNECT without Authorization header throws AuthenticationCredentialsNotFoundException`() {
        val message = buildConnectMessage(null)

        assertThrows<AuthenticationCredentialsNotFoundException> {
            interceptor.preSend(message, channel)
        }
    }

    @Test
    fun `CONNECT with invalid JWT throws AuthenticationCredentialsNotFoundException`() {
        whenever(jwtDecoder.decode("invalid-token"))
            .thenThrow(org.springframework.security.oauth2.jwt.BadJwtException("expired"))

        val message = buildConnectMessage("invalid-token")

        assertThrows<AuthenticationCredentialsNotFoundException> {
            interceptor.preSend(message, channel)
        }
    }

    // ------ SUBSCRIBE Tests ------

    @Test
    fun `SUBSCRIBE to workspace topic with valid access is allowed`() {
        val auth = buildAuthToken(listOf(testWorkspaceId))
        val message = buildSubscribeMessage(
            "/topic/workspace/$testWorkspaceId/entities",
            auth
        )

        val result = interceptor.preSend(message, channel)
        assertNotNull(result)
    }

    @Test
    fun `SUBSCRIBE to workspace topic without access is rejected`() {
        val otherWorkspaceId = UUID.randomUUID()
        val auth = buildAuthToken(listOf(testWorkspaceId)) // Has access to testWorkspaceId only

        val message = buildSubscribeMessage(
            "/topic/workspace/$otherWorkspaceId/entities", // Trying to access different workspace
            auth
        )

        assertThrows<AccessDeniedException> {
            interceptor.preSend(message, channel)
        }
    }

    @Test
    fun `SUBSCRIBE without prior CONNECT authentication is rejected`() {
        val message = buildSubscribeMessage(
            "/topic/workspace/$testWorkspaceId/entities",
            null // No authentication
        )

        assertThrows<AuthenticationCredentialsNotFoundException> {
            interceptor.preSend(message, channel)
        }
    }

    @Test
    fun `SUBSCRIBE to non-workspace topic is rejected with default-deny`() {
        val auth = buildAuthToken(emptyList())
        val message = buildSubscribeMessage("/topic/system/health", auth)

        assertThrows<AccessDeniedException> {
            interceptor.preSend(message, channel)
        }
    }

    // ------ extractWorkspaceId Tests ------

    @Test
    fun `extractWorkspaceId parses valid workspace topic`() {
        val id = UUID.randomUUID()
        val result = WebSocketSecurityInterceptor.extractWorkspaceId("/topic/workspace/$id/entities")
        assertEquals(id, result)
    }

    @Test
    fun `extractWorkspaceId returns null for non-workspace topic`() {
        val result = WebSocketSecurityInterceptor.extractWorkspaceId("/topic/system/health")
        assertNull(result)
    }

    @Test
    fun `extractWorkspaceId returns null for malformed workspace ID`() {
        val result = WebSocketSecurityInterceptor.extractWorkspaceId("/topic/workspace/not-a-uuid/entities")
        assertNull(result)
    }
}
