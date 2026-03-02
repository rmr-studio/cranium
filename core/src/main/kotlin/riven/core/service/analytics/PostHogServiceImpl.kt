package riven.core.service.analytics

import com.posthog.server.PostHogCaptureOptions
import com.posthog.server.PostHogInterface
import io.github.oshai.kotlinlogging.KLogger
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.micrometer.core.instrument.Counter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PostHogServiceImpl(
    private val client: PostHogInterface,
    private val circuitBreaker: CircuitBreaker,
    private val captureCounter: Counter,
    private val failureCounter: Counter,
    private val circuitOpenCounter: Counter,
    private val logger: KLogger
) : PostHogService {

    private val seenWorkspaces = ConcurrentHashMap.newKeySet<UUID>()

    // ------ Public API ------

    override fun capture(userId: UUID, workspaceId: UUID, event: String, properties: Map<String, Any>) {
        captureCounter.increment()
        lazyGroupIdentify(userId, workspaceId)
        try {
            circuitBreaker.executeRunnable {
                val options = PostHogCaptureOptions.builder()
                    .properties(properties)
                    .group("workspace", workspaceId.toString())
                    .build()
                client.capture(userId.toString(), event, options)
            }
        } catch (e: Throwable) {
            handleFailure("capture", e)
        }
    }

    override fun identify(userId: UUID, properties: Map<String, Any>) {
        try {
            circuitBreaker.executeRunnable {
                client.identify(userId.toString(), properties)
            }
        } catch (e: Throwable) {
            handleFailure("identify", e)
        }
    }

    override fun groupIdentify(userId: UUID, workspaceId: UUID, properties: Map<String, Any>) {
        try {
            circuitBreaker.executeRunnable {
                client.group(userId.toString(), "workspace", workspaceId.toString(), properties)
            }
            seenWorkspaces.add(workspaceId)
        } catch (e: Throwable) {
            handleFailure("groupIdentify", e)
        }
    }

    // ------ Private Helpers ------

    /**
     * On first encounter of a workspace within this JVM lifetime, sends a bare group call
     * so that subsequent capture events are correctly associated with the workspace group.
     */
    private fun lazyGroupIdentify(userId: UUID, workspaceId: UUID) {
        if (seenWorkspaces.add(workspaceId)) {
            try {
                circuitBreaker.executeRunnable {
                    client.group(userId.toString(), "workspace", workspaceId.toString(), emptyMap())
                }
            } catch (e: Throwable) {
                handleFailure("lazyGroupIdentify", e)
            }
        }
    }

    private fun handleFailure(operation: String, e: Throwable) {
        failureCounter.increment()
        if (e is CallNotPermittedException) {
            circuitOpenCounter.increment()
            logger.warn { "PostHog $operation rejected by circuit breaker (OPEN state)" }
        } else {
            logger.warn { "PostHog $operation failed: ${e::class.simpleName} - ${e.message}" }
        }
    }
}
