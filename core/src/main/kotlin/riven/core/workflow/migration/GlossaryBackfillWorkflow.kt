package riven.core.workflow.migration

import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.time.Duration
import java.util.UUID

/**
 * Paginated, idempotent backfill of legacy `workspace_business_definitions` rows into
 * the entity layer (`entity_type=glossary`).
 *
 * One run per workspace. Iterates pages of `definitionIds` until [GlossaryBackfillPage.nextCursor]
 * is null. Each page is delegated to [GlossaryBackfillActivities.migrateBatch], which is
 * the unit of idempotency — re-running the workflow against an already-migrated workspace
 * yields a result of (migrated=0, skipped=N, failed=0).
 */
@WorkflowInterface
interface GlossaryBackfillWorkflow {

    @WorkflowMethod
    fun run(workspaceId: UUID): GlossaryBackfillBatchResult
}

class GlossaryBackfillWorkflowImpl : GlossaryBackfillWorkflow {

    private val activities: GlossaryBackfillActivities = Workflow.newActivityStub(
        GlossaryBackfillActivities::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setHeartbeatTimeout(Duration.ofMinutes(2))
            .build(),
    )

    override fun run(workspaceId: UUID): GlossaryBackfillBatchResult {
        var totalMigrated = 0
        var totalSkipped = 0
        var totalFailed = 0
        var cursor: GlossaryBackfillCursor? = null

        while (true) {
            val page = activities.fetchPage(workspaceId, cursor)
            if (page.definitionIds.isEmpty()) break
            val result = activities.migrateBatch(workspaceId, page.definitionIds)
            totalMigrated += result.migrated
            totalSkipped += result.skipped
            totalFailed += result.failed
            cursor = page.nextCursor ?: break
        }

        return GlossaryBackfillBatchResult(totalMigrated, totalSkipped, totalFailed)
    }
}
