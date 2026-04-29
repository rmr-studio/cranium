package riven.core.workflow.migration

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import java.util.UUID

/**
 * Temporal activity contract for the legacy `workspace_business_definitions` -> entity-backed
 * glossary backfill.
 *
 * The workflow drives a paginated walk over `workspace_business_definitions` rows (via
 * [fetchPage]) and delegates the actual transactional work to [migrateBatch], which is
 * responsible for being idempotent: re-runs over the same `definitionId` set must skip
 * already-migrated rows rather than producing duplicate glossary entities.
 */
@ActivityInterface
interface GlossaryBackfillActivities {

    @ActivityMethod
    fun fetchPage(workspaceId: UUID, cursor: GlossaryBackfillCursor?): GlossaryBackfillPage

    @ActivityMethod
    fun migrateBatch(workspaceId: UUID, definitionIds: List<UUID>): GlossaryBackfillBatchResult
}

/**
 * Stable keyset cursor over `workspace_business_definitions(created_at, id)`. Strings
 * are used for `createdAt` to keep the cursor JSON-serialisable across Temporal
 * activity boundaries.
 */
data class GlossaryBackfillCursor(
    val createdAt: String,
    val definitionId: UUID,
)

data class GlossaryBackfillPage(
    val definitionIds: List<UUID>,
    val nextCursor: GlossaryBackfillCursor?,
)

data class GlossaryBackfillBatchResult(
    val migrated: Int,
    val skipped: Int,
    val failed: Int,
)
