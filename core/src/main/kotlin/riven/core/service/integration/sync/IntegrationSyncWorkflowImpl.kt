package riven.core.service.integration.sync

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import riven.core.configuration.workflow.RetryConfig
import riven.core.models.integration.sync.IntegrationSyncWorkflowInput
import java.time.Duration

/**
 * Temporal workflow implementation for integration data sync.
 *
 * Orchestrates three activities in sequence:
 * 1. [IntegrationSyncActivities.transitionToSyncing] — mark connection as SYNCING
 * 2. [IntegrationSyncActivities.fetchAndProcessRecords] — paginated fetch + entity upsert
 * 3. [IntegrationSyncActivities.finalizeSyncState] — write final sync outcome
 *
 * Activity timeout is set to 4 hours to accommodate initial syncs that may process
 * 50k+ records. Heartbeat timeout is 30 seconds to detect hung workers promptly.
 *
 * NOT a Spring bean — Temporal manages lifecycle. Spring configuration is injected
 * via [riven.core.configuration.workflow.TemporalWorkerConfiguration] factory.
 *
 * @param retryConfig Retry configuration injected via workflow factory from application.yml
 */
class IntegrationSyncWorkflowImpl(
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

        activities.finalizeSyncState(input.connectionId, result.entityTypeId, result)
        logger.info("Finalized sync state for connection=${input.connectionId}, entityType=${result.entityTypeId}")
    }

    /**
     * Creates the activity stub with retry and timeout configuration.
     *
     * Start-to-close timeout is 4 hours to handle large initial syncs.
     * Heartbeat timeout is 30 seconds to detect worker failures promptly.
     * Retry policy mirrors the integrationSync configuration from application.yml.
     */
    private fun createActivitiesStub(): IntegrationSyncActivities =
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
