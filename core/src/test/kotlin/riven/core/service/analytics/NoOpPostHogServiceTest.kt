package riven.core.service.analytics

import io.micrometer.core.instrument.Counter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.UUID

class NoOpPostHogServiceTest {

    private lateinit var captureCounter: Counter
    private lateinit var service: NoOpPostHogService

    private val userId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")

    @BeforeEach
    fun setup() {
        captureCounter = mock()
        service = NoOpPostHogService(captureCounter)
    }

    @Test
    fun `capture increments counter but does nothing else`() {
        service.capture(userId, workspaceId, "test_event", mapOf("key" to "value"))

        verify(captureCounter).increment()
    }

    @Test
    fun `identify does nothing`() {
        service.identify(userId, mapOf("role" to "admin"))
    }

    @Test
    fun `groupIdentify does nothing`() {
        service.groupIdentify(userId, workspaceId, mapOf("name" to "workspace"))
    }

    @Test
    fun `capture never throws regardless of input`() {
        service.capture(userId, workspaceId, "", emptyMap())
        service.capture(userId, workspaceId, "event", mapOf("key" to listOf(1, 2, 3)))

        verify(captureCounter, times(2)).increment()
    }
}
