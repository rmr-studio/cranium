package riven.core.filter.analytics

import io.github.oshai.kotlinlogging.KLogger
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.servlet.HandlerMapping
import riven.core.service.analytics.PostHogService
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostHogCaptureFilterTest {

    private lateinit var postHogService: PostHogService
    private lateinit var kLogger: KLogger
    private lateinit var filter: PostHogCaptureFilter
    private lateinit var filterChain: FilterChain

    private val userId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId = UUID.fromString("a1b2c3d4-5e6f-7890-abcd-ef1234567890")

    @BeforeEach
    fun setup() {
        postHogService = mock()
        kLogger = mock()
        filter = PostHogCaptureFilter(postHogService, kLogger)
        filterChain = mock()
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    // ------ Test Helpers ------

    private fun setSecurityContext(subjectId: UUID = userId) {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject(subjectId.toString())
            .build()
        val auth = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun createAuthenticatedRequest(
        method: String = "GET",
        uri: String = "/api/v1/$workspaceId/entities",
        routeTemplate: String? = "/api/v1/{workspaceId}/entities",
        pathVariables: Map<String, String>? = mapOf("workspaceId" to workspaceId.toString())
    ): MockHttpServletRequest {
        val request = MockHttpServletRequest(method, uri)
        routeTemplate?.let {
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, it)
        }
        pathVariables?.let {
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, it)
        }
        return request
    }

    // ------ Tests ------

    @Test
    fun `captures authenticated API request with correct event properties`() {
        setSecurityContext()
        val request = createAuthenticatedRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)

        val userIdCaptor = argumentCaptor<UUID>()
        val workspaceIdCaptor = argumentCaptor<UUID>()
        val eventCaptor = argumentCaptor<String>()
        val propsCaptor = argumentCaptor<Map<String, Any>>()

        verify(postHogService).capture(
            userIdCaptor.capture(),
            workspaceIdCaptor.capture(),
            eventCaptor.capture(),
            propsCaptor.capture()
        )

        assertEquals(userId, userIdCaptor.firstValue)
        assertEquals(workspaceId, workspaceIdCaptor.firstValue)
        assertEquals("\$api_request", eventCaptor.firstValue)

        val props = propsCaptor.firstValue
        assertEquals("GET", props["method"])
        assertEquals("/api/v1/{workspaceId}/entities", props["endpoint"])
        assertEquals(200, props["statusCode"])
        assertTrue((props["latencyMs"] as Long) >= 0)
        assertEquals(false, props["isError"])
    }

    @Test
    fun `uses route template as endpoint when available`() {
        setSecurityContext()
        val request = createAuthenticatedRequest(
            uri = "/api/v1/$workspaceId/entities/$userId",
            routeTemplate = "/api/v1/{workspaceId}/entities/{id}",
            pathVariables = mapOf("workspaceId" to workspaceId.toString(), "id" to userId.toString())
        )
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        val propsCaptor = argumentCaptor<Map<String, Any>>()
        verify(postHogService).capture(any(), any(), any(), propsCaptor.capture())

        assertEquals("/api/v1/{workspaceId}/entities/{id}", propsCaptor.firstValue["endpoint"])
    }

    @Test
    fun `normalizes UUIDs in URI when route template is unavailable`() {
        setSecurityContext()
        val request = createAuthenticatedRequest(
            uri = "/api/v1/a1b2c3d4-5e6f-7890-abcd-ef1234567890/entities/f8b1c2d3-4e5f-6789-abcd-ef0123456789",
            routeTemplate = null,
            pathVariables = mapOf("workspaceId" to workspaceId.toString())
        )
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        val propsCaptor = argumentCaptor<Map<String, Any>>()
        verify(postHogService).capture(any(), any(), any(), propsCaptor.capture())

        assertEquals("/api/v1/{id}/entities/{id}", propsCaptor.firstValue["endpoint"])
    }

    @Test
    fun `skips actuator requests`() {
        setSecurityContext()
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(postHogService, never()).capture(any(), any(), any(), any())
    }

    @Test
    fun `skips swagger-ui requests`() {
        setSecurityContext()
        val request = MockHttpServletRequest("GET", "/swagger-ui/index.html")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(postHogService, never()).capture(any(), any(), any(), any())
    }

    @Test
    fun `skips docs requests`() {
        setSecurityContext()
        val request = MockHttpServletRequest("GET", "/docs/openapi.json")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(postHogService, never()).capture(any(), any(), any(), any())
    }

    @Test
    fun `skips unauthenticated requests silently`() {
        // Do NOT set SecurityContext
        val request = createAuthenticatedRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(postHogService, never()).capture(any(), any(), any(), any())
    }

    @Test
    fun `includes error context on 4xx responses`() {
        setSecurityContext()
        val request = createAuthenticatedRequest()
        val response = MockHttpServletResponse()

        whenever(filterChain.doFilter(any(), any())).thenAnswer {
            response.status = 404
            request.setAttribute("posthog.error.class", "NotFoundException")
        }

        filter.doFilter(request, response, filterChain)

        val propsCaptor = argumentCaptor<Map<String, Any>>()
        verify(postHogService).capture(any(), any(), any(), propsCaptor.capture())

        val props = propsCaptor.firstValue
        assertEquals(true, props["isError"])
        assertEquals(404, props["statusCode"])
        assertEquals("NotFoundException", props["errorClass"])
        assertFalse(props.containsKey("errorMessage"))
    }

    @Test
    fun `includes error context on 5xx responses`() {
        setSecurityContext()
        val request = createAuthenticatedRequest()
        val response = MockHttpServletResponse()

        whenever(filterChain.doFilter(any(), any())).thenAnswer {
            response.status = 500
            request.setAttribute("posthog.error.class", "RuntimeException")
        }

        filter.doFilter(request, response, filterChain)

        val propsCaptor = argumentCaptor<Map<String, Any>>()
        verify(postHogService).capture(any(), any(), any(), propsCaptor.capture())

        val props = propsCaptor.firstValue
        assertEquals(true, props["isError"])
        assertEquals(500, props["statusCode"])
        assertEquals("RuntimeException", props["errorClass"])
        assertFalse(props.containsKey("errorMessage"))
    }

    @Test
    fun `does not include error context on 2xx responses`() {
        setSecurityContext()
        val request = createAuthenticatedRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        val propsCaptor = argumentCaptor<Map<String, Any>>()
        verify(postHogService).capture(any(), any(), any(), propsCaptor.capture())

        val props = propsCaptor.firstValue
        assertEquals(false, props["isError"])
        assertEquals(200, props["statusCode"])
        assertFalse(props.containsKey("errorClass"))
        assertFalse(props.containsKey("errorMessage"))
    }

    @Test
    fun `skips capture when workspaceId is not in path`() {
        setSecurityContext()
        val request = createAuthenticatedRequest(
            uri = "/api/v1/health",
            routeTemplate = "/api/v1/health",
            pathVariables = null
        )
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify(filterChain).doFilter(request, response)
        verify(postHogService, never()).capture(any(), any(), any(), any())
    }

    @Test
    fun `measures latency in milliseconds`() {
        setSecurityContext()
        val request = createAuthenticatedRequest()
        val response = MockHttpServletResponse()

        whenever(filterChain.doFilter(any(), any())).thenAnswer {
            Thread.sleep(50)
        }

        filter.doFilter(request, response, filterChain)

        val propsCaptor = argumentCaptor<Map<String, Any>>()
        verify(postHogService).capture(any(), any(), any(), propsCaptor.capture())

        val latencyMs = propsCaptor.firstValue["latencyMs"] as Long
        assertTrue(latencyMs >= 50, "Expected latency >= 50ms but got ${latencyMs}ms")
    }
}
