package riven.core.models.integration.sync

import java.util.UUID

/**
 * Result DTO returned by the fetchAndProcessRecords activity.
 *
 * Contains the outcome of processing all records for a single sync run.
 * Passed from fetchAndProcessRecords to finalizeSyncState to update the
 * IntegrationSyncStateEntity with the final sync outcome.
 *
 * @property entityTypeId UUID of the EntityType that was synced into
 * @property cursor New cursor value to persist for incremental sync (null if full sync or no cursor)
 * @property recordsSynced Total number of records successfully upserted
 * @property recordsFailed Number of records that failed to upsert
 * @property lastErrorMessage Last error message encountered (null if all records succeeded)
 * @property success Whether the sync completed without fatal errors
 */
data class SyncProcessingResult(
    val entityTypeId: UUID,
    val cursor: String? = null,
    val recordsSynced: Int,
    val recordsFailed: Int,
    val lastErrorMessage: String? = null,
    val success: Boolean
)
