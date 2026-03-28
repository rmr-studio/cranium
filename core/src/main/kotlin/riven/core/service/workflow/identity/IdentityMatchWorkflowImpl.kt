package riven.core.service.workflow.identity

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import java.time.Duration
import java.util.UUID

/**
 * Implementation of [IdentityMatchWorkflow] for the identity matching pipeline.
 *
 * This class is NOT a Spring bean — Temporal manages its lifecycle. It is instantiated by
 * Temporal's worker via a factory in [riven.core.configuration.workflow.TemporalWorkerConfiguration].
 *
 * DETERMINISM RULES:
 * - Uses Workflow.getLogger() for logging
 * - Uses Workflow.newActivityStub() for all side effects
 * - No Spring injection — uses no-arg constructor
 * - No direct database access, no HTTP calls
 *
 * Activity options: startToCloseTimeout = 30s, 3 max attempts with exponential backoff.
 */
class IdentityMatchWorkflowImpl : IdentityMatchWorkflow {

    private val logger = Workflow.getLogger(IdentityMatchWorkflowImpl::class.java)

    private val activities: IdentityMatchActivities = Workflow.newActivityStub(
        IdentityMatchActivities::class.java,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(
                RetryOptions.newBuilder()
                    .setMaximumAttempts(3)
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .build()
            )
            .build()
    )

    override fun matchEntity(entityId: UUID, workspaceId: UUID, userId: UUID?): Int {
        logger.info("Starting identity match pipeline for entityId=$entityId workspaceId=$workspaceId")

        val candidates = activities.findCandidates(entityId, workspaceId)
        if (candidates.isEmpty()) {
            logger.info("No candidates found for entityId=$entityId — pipeline short-circuited")
            return 0
        }

        val scored = activities.scoreCandidates(entityId, workspaceId, candidates)
        if (scored.isEmpty()) {
            logger.info("No candidates above threshold for entityId=$entityId — pipeline short-circuited")
            return 0
        }

        val count = activities.persistSuggestions(workspaceId, scored, userId)
        logger.info("Identity match pipeline complete for entityId=$entityId — $count suggestions persisted")
        return count
    }
}
