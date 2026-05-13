package cranium.core.service.integration.sync

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import cranium.core.configuration.workflow.RetryConfig
import cranium.core.models.integration.sync.IntegrationSyncWorkflowInput
import java.time.Duration

/**
 * Temporal workflow implementation for integration data sync.
 *
 * Orchestrates five activities in sequence:
 * 1. [IntegrationSyncActivities.transitionToSyncing] — mark connection as SYNCING
 * 2. [IntegrationSyncActivities.fetchAndProcessRecords] — paginated fetch + entity upsert
 * 3. [IntegrationSyncActivities.finalizeSyncState] — write final sync outcome
 * 4. [IntegrationSyncActivities.executeProjections] — projection pipeline (no-op stub)
 * 5. [IntegrationSyncActivities.evaluateHealth] — derive ConnectionStatus from sync outcomes
 *
 * Activity timeout is set to 4 hours to accommodate initial syncs that may process
 * 50k+ records. Heartbeat timeout is 30 seconds to detect hung workers promptly.
 *
 * The evaluateHealth activity is wrapped in a try-catch — health evaluation failure is
 * non-fatal. Sync state is the source of truth; health status is derived.
 *
 * NOT a Spring bean — Temporal manages lifecycle. Spring configuration is injected
 * via [cranium.core.configuration.workflow.TemporalWorkerConfiguration] factory.
 *
 * @param retryConfig Retry configuration injected via workflow factory from application.yml
 */
open class IntegrationSyncWorkflowImpl(
    private val retryConfig: RetryConfig
) : IntegrationSyncWorkflow {

    private val logger = Workflow.getLogger(IntegrationSyncWorkflowImpl::class.java)

    override fun execute(input: IntegrationSyncWorkflowInput) {
        logger.info("Starting integration sync for connection=${input.connectionId}, model=${input.model}")

        val activities = createActivitiesStub()

        activities.transitionToSyncing(input.connectionId, input.workspaceId)
        logger.info("Transitioned connection=${input.connectionId} to SYNCING")

        val result = activities.fetchAndProcessRecords(input)
        logger.info(
            "Processed records for connection=${input.connectionId}, model=${input.model}: " +
                "synced=${result.recordsSynced}, failed=${result.recordsFailed}, success=${result.success}"
        )

        val entityTypeId = result.entityTypeId
        if (entityTypeId == null) {
            logger.warn("Skipping finalizeSyncState — model context resolution failed for connection=${input.connectionId}, model=${input.model}")
        } else {
            activities.finalizeSyncState(input.connectionId, entityTypeId, result)
            logger.info("Finalized sync state for connection=${input.connectionId}, entityType=$entityTypeId")
        }

        // Pass 3: Projection pipeline (no-op stub until ingestion-pipeline branch)
        if (result.success && result.recordsSynced > 0) {
            val projectionEntityTypeId = requireNotNull(result.entityTypeId) {
                "entityTypeId must not be null when result.success is true and recordsSynced > 0"
            }
            activities.executeProjections(input.connectionId, input.workspaceId, projectionEntityTypeId, result.syncedEntityIds)
            logger.info("Executed projections for connection=${input.connectionId}, entityType=$projectionEntityTypeId, entities=${result.syncedEntityIds.size}")
        }

        try {
            activities.evaluateHealth(input.connectionId)
            logger.info("Evaluated health for connection=${input.connectionId}")
        } catch (e: Exception) {
            // Health evaluation failure is non-fatal — sync state committed successfully above.
            // Log is handled inside the activity; workflow proceeds to completion.
            logger.warn("Health evaluation failed for connection=${input.connectionId} — continuing: ${e.message}")
        }
    }

    /**
     * Creates the activity stub with retry and timeout configuration.
     *
     * Start-to-close timeout is 4 hours to handle large initial syncs.
     * Heartbeat timeout is 30 seconds to detect worker failures promptly.
     * Retry policy mirrors the integrationSync configuration from application.yml.
     *
     * Internal open to allow test subclasses to inject mock activity stubs without
     * requiring a live Temporal execution context.
     */
    internal open fun createActivitiesStub(): IntegrationSyncActivities =
        Workflow.newActivityStub(
            IntegrationSyncActivities::class.java,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofHours(4))
                .setHeartbeatTimeout(Duration.ofSeconds(30))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(retryConfig.maxAttempts)
                        .setInitialInterval(Duration.ofSeconds(retryConfig.initialIntervalSeconds))
                        .setBackoffCoefficient(retryConfig.backoffCoefficient)
                        .setMaximumInterval(Duration.ofSeconds(retryConfig.maxIntervalSeconds))
                        .build()
                )
                .build()
        )
}
