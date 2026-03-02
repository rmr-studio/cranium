package riven.core.service.analytics

import io.micrometer.core.instrument.Counter
import java.util.UUID

class NoOpPostHogService(
    private val captureCounter: Counter
) : PostHogService {

    override fun capture(userId: UUID, workspaceId: UUID, event: String, properties: Map<String, Any>) {
        captureCounter.increment()
    }

    override fun identify(userId: UUID, properties: Map<String, Any>) {
        // No-op
    }

    override fun groupIdentify(userId: UUID, workspaceId: UUID, properties: Map<String, Any>) {
        // No-op
    }
}
