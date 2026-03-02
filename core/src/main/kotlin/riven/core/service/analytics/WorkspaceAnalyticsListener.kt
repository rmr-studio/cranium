package riven.core.service.analytics

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import riven.core.models.analytics.WorkspaceCreatedEvent
import riven.core.models.analytics.WorkspaceUpdatedEvent
import java.time.ZonedDateTime

@Component
class WorkspaceAnalyticsListener(
    private val postHogService: PostHogService
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onWorkspaceCreated(event: WorkspaceCreatedEvent) {
        postHogService.groupIdentify(
            userId = event.userId,
            workspaceId = event.workspaceId,
            properties = buildGroupProperties(event.workspaceName, event.createdAt, event.memberCount)
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onWorkspaceUpdated(event: WorkspaceUpdatedEvent) {
        postHogService.groupIdentify(
            userId = event.userId,
            workspaceId = event.workspaceId,
            properties = buildGroupProperties(event.workspaceName, event.createdAt, event.memberCount)
        )
    }

    private fun buildGroupProperties(name: String, createdAt: ZonedDateTime?, memberCount: Int): Map<String, Any> =
        buildMap {
            put("name", name)
            createdAt?.let { put("createdAt", it.toString()) }
            put("memberCount", memberCount)
        }
}
