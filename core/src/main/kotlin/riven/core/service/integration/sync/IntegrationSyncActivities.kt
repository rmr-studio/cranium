package riven.core.service.integration.sync

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import riven.core.models.integration.sync.IntegrationSyncWorkflowInput
import riven.core.models.integration.sync.SyncProcessingResult
import java.util.UUID

/**
 * Activity interface for the integration sync workflow.
 *
 * Four activities correspond to the four phases of a sync run:
 * 1. [transitionToSyncing] — lightweight status write before any work begins
 * 2. [fetchAndProcessRecords] — long-running paginated fetch and entity upsert
 * 3. [finalizeSyncState] — single sync state write after processing completes
 * 4. [evaluateHealth] — derives ConnectionStatus from sync outcomes (separate transaction)
 *
 * Implementations live in Plan 02 (`IntegrationSyncActivitiesImpl`).
 *
 * @see IntegrationSyncWorkflowImpl
 */
@ActivityInterface
interface IntegrationSyncActivities {

    /**
     * Transitions the integration connection sync state to SYNCING status.
     *
     * This lightweight activity runs before the main fetch so that sync state
     * reflects an in-progress sync as early as possible.
     *
     * @param connectionId Internal UUID of the IntegrationConnectionEntity
     * @param workspaceId Workspace that owns this connection
     */
    @ActivityMethod
    fun transitionToSyncing(connectionId: UUID, workspaceId: UUID)

    /**
     * Fetches all records from Nango for the given model and upserts them as entities.
     *
     * This is the long-running activity. It:
     * 1. Fetches paginated records from the Nango API
     * 2. Upserts each record as an EntityEntity (Pass 1)
     * 3. Collects pending relationships for batch resolution (Pass 2)
     * 4. Returns processing statistics and the entity type ID for finalization
     *
     * Heartbeats must be sent during pagination to prevent activity timeout.
     *
     * @param input Full workflow input containing connection, workspace, and sync context
     * @return Processing result including entity type ID, record counts, and cursor
     */
    @ActivityMethod
    fun fetchAndProcessRecords(input: IntegrationSyncWorkflowInput): SyncProcessingResult

    /**
     * Finalizes the sync state after processing completes.
     *
     * Updates IntegrationSyncStateEntity with the outcome of the sync run:
     * cursor, record counts, error message, and final status (SYNCED or FAILED).
     *
     * @param connectionId Internal UUID of the IntegrationConnectionEntity
     * @param entityTypeId UUID of the EntityType that was synced into
     * @param result Processing result from fetchAndProcessRecords
     */
    @ActivityMethod
    fun finalizeSyncState(connectionId: UUID, entityTypeId: UUID, result: SyncProcessingResult)

    /**
     * Evaluates connection health by aggregating sync state outcomes and updating ConnectionStatus.
     *
     * Runs as a separate 4th activity so that health status writes have an independent
     * transaction boundary from sync state persistence. A failure here does not roll back
     * or affect the sync state committed in [finalizeSyncState].
     *
     * The workflow wraps this in a try-catch — health evaluation failure is non-fatal.
     *
     * @param connectionId Internal UUID of the IntegrationConnectionEntity
     */
    @ActivityMethod
    fun evaluateHealth(connectionId: UUID)
}
