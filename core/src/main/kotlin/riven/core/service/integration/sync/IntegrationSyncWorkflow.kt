package cranium.core.service.integration.sync

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import cranium.core.models.integration.sync.IntegrationSyncWorkflowInput

/**
 * Temporal workflow interface for executing an integration data sync.
 *
 * A single workflow instance handles one connection+model combination per sync run.
 * The deterministic workflow ID `sync-{connectionId}-{model}` ensures that duplicate
 * webhook deliveries from Nango do not result in concurrent sync executions.
 *
 * DETERMINISM RULES:
 * - All side effects (DB, HTTP, timestamps) go through activities
 * - Use Workflow.getLogger() for logging (not KLogger)
 * - Use Workflow.randomUUID() if random IDs needed
 *
 * @see IntegrationSyncWorkflowImpl
 */
@WorkflowInterface
interface IntegrationSyncWorkflow {

    /**
     * Execute a full or incremental sync for the given connection and model.
     *
     * Results are written to IntegrationSyncStateEntity by the finalizeSyncState activity.
     * The workflow has no return value — outcome is observable via the sync state table.
     *
     * @param input All context needed to identify the connection, workspace, and model to sync
     */
    @WorkflowMethod
    fun execute(input: IntegrationSyncWorkflowInput)
}
