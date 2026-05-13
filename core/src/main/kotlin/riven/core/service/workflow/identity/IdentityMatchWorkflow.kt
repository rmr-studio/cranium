package cranium.core.service.workflow.identity

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.util.UUID

/**
 * Temporal workflow interface for the identity matching pipeline.
 *
 * Orchestrates three independently retryable activities: FindCandidates, ScoreCandidates,
 * and PersistSuggestions. Each activity step short-circuits (returns 0) if the previous
 * step yields no results.
 *
 * **Workflow ID convention:** Callers MUST set the workflow ID using the companion helper to
 * ensure Temporal-level deduplication for duplicate entity triggers:
 * ```kotlin
 * WorkflowOptions.newBuilder()
 *     .setWorkflowId(IdentityMatchWorkflow.workflowId(entityId))
 *     .build()
 * ```
 * This guarantees that if the same entity triggers matching concurrently, only one workflow
 * execution runs — the duplicate start is rejected by Temporal at the server level.
 *
 * @see IdentityMatchWorkflowImpl
 */
@WorkflowInterface
interface IdentityMatchWorkflow {

    /**
     * Runs the full identity matching pipeline for the given entity.
     *
     * @param entityId the entity to match against all others in the workspace
     * @param workspaceId workspace scope — candidates must be in the same workspace
     * @param userId the initiating user, or null for system-triggered runs
     * @return the number of new match suggestions persisted (0 if no matches found)
     */
    @WorkflowMethod
    fun matchEntity(entityId: UUID, workspaceId: UUID, userId: UUID?): Int

    companion object {
        /**
         * Derives the canonical Temporal workflow ID for an identity match run.
         *
         * Use this when starting or signalling the workflow to ensure Temporal-level
         * deduplication for the same entity:
         * ```kotlin
         * WorkflowOptions.newBuilder()
         *     .setWorkflowId(IdentityMatchWorkflow.workflowId(entityId))
         *     .build()
         * ```
         *
         * @param entityId the entity being matched
         * @return workflow ID in the format "identity-match-{entityId}"
         */
        fun workflowId(entityId: UUID): String = "identity-match-$entityId"
    }
}
