package riven.core.service.analytics

import com.posthog.server.PostHogCaptureOptions
import com.posthog.server.PostHogInterface
import io.github.oshai.kotlinlogging.KLogger
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.Counter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.UUID

class PostHogServiceImplTest {

    private lateinit var client: PostHogInterface
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var captureCounter: Counter
    private lateinit var failureCounter: Counter
    private lateinit var circuitOpenCounter: Counter
    private lateinit var logger: KLogger
    private lateinit var service: PostHogServiceImpl

    private val userId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")

    @BeforeEach
    fun setup() {
        client = mock()
        captureCounter = mock()
        failureCounter = mock()
        circuitOpenCounter = mock()
        logger = mock()

        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .build()
        val registry = CircuitBreakerRegistry.of(config)
        circuitBreaker = registry.circuitBreaker("posthog-test")

        service = PostHogServiceImpl(
            client = client,
            circuitBreaker = circuitBreaker,
            captureCounter = captureCounter,
            failureCounter = failureCounter,
            circuitOpenCounter = circuitOpenCounter,
            logger = logger
        )
    }

    @Test
    fun `capture calls SDK with correct parameters and groups`() {
        val properties = mapOf<String, Any>("method" to "GET", "endpoint" to "/api/v1/test")

        service.capture(userId, workspaceId, "api_request", properties)

        verify(client).capture(
            eq(userId.toString()),
            eq("api_request"),
            any<PostHogCaptureOptions>()
        )
        verify(captureCounter).increment()
        verify(failureCounter, never()).increment()
    }

    @Test
    fun `capture swallows SDK exceptions and increments failure counter`() {
        whenever(client.group(any(), any(), any(), any())).thenAnswer { /* no-op for lazy group */ }
        whenever(client.capture(any(), any(), any<PostHogCaptureOptions>())).thenThrow(RuntimeException("Network error"))

        service.capture(userId, workspaceId, "api_request", emptyMap())

        verify(captureCounter).increment()
        verify(failureCounter).increment()
    }

    @Test
    fun `capture fires lazy groupIdentify on first call for a workspace`() {
        service.capture(userId, workspaceId, "api_request", emptyMap())

        verify(client).group(
            eq(userId.toString()),
            eq("workspace"),
            eq(workspaceId.toString()),
            eq(emptyMap())
        )
        verify(client).capture(any(), any(), any<PostHogCaptureOptions>())
    }

    @Test
    fun `capture does not fire lazy groupIdentify on second call for same workspace`() {
        service.capture(userId, workspaceId, "api_request", emptyMap())
        reset(client)

        service.capture(userId, workspaceId, "api_request", emptyMap())

        verify(client, never()).group(any(), any(), any(), any())
        verify(client).capture(any(), any(), any<PostHogCaptureOptions>())
    }

    @Test
    fun `identify calls SDK with correct parameters`() {
        val props = mapOf<String, Any>("role" to "admin")

        service.identify(userId, props)

        verify(client).identify(eq(userId.toString()), eq(props))
    }

    @Test
    fun `identify swallows exceptions`() {
        whenever(client.identify(any(), any<Map<String, Any>>())).thenThrow(RuntimeException("fail"))

        service.identify(userId, mapOf("role" to "admin"))

        verify(failureCounter).increment()
    }

    @Test
    fun `groupIdentify calls SDK group with correct parameters`() {
        val props = mapOf<String, Any>("name" to "My Workspace", "memberCount" to 5)

        service.groupIdentify(userId, workspaceId, props)

        verify(client).group(
            eq(userId.toString()),
            eq("workspace"),
            eq(workspaceId.toString()),
            eq(props)
        )
    }

    @Test
    fun `groupIdentify marks workspace as seen for lazy identify`() {
        service.groupIdentify(userId, workspaceId, mapOf("name" to "Test"))
        reset(client)

        service.capture(userId, workspaceId, "test_event", emptyMap())

        verify(client, never()).group(any(), any(), any(), any())
    }

    @Test
    fun `groupIdentify swallows exceptions`() {
        whenever(client.group(any(), any(), any(), any())).thenThrow(RuntimeException("fail"))

        service.groupIdentify(userId, workspaceId, emptyMap())

        verify(failureCounter).increment()
    }
}
