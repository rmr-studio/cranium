package cranium.core.models.integration.sync

import cranium.core.enums.integration.SyncKeyType
import java.util.UUID

/**
 * Result DTO returned by the fetchAndProcessRecords activity.
 *
 * Contains the outcome of processing all records for a single sync run.
 * Passed from fetchAndProcessRecords to finalizeSyncState to update the
 * IntegrationSyncStateEntity with the final sync outcome.
 *
 * @property entityTypeId UUID of the EntityType that was synced into, or null if model context resolution failed
 * @property cursor New cursor value to persist for incremental sync (null if full sync or no cursor)
 * @property recordsSynced Total number of records successfully upserted
 * @property recordsFailed Number of records that failed to upsert
 * @property lastErrorMessage Last error message encountered (null if all records succeeded)
 * @property success Whether the sync completed without fatal errors
 * @property syncedEntityIds UUIDs of entities successfully upserted during Pass 1, for downstream projection
 * @property syncKey Disambiguation key for sync state (e.g. [SyncKeyType.NOTE_EMBEDDING]). Null for standard entity syncs.
 */
data class SyncProcessingResult(
    val entityTypeId: UUID?,
    val cursor: String? = null,
    val recordsSynced: Int,
    val recordsFailed: Int,
    val lastErrorMessage: String? = null,
    val success: Boolean,
    val syncedEntityIds: List<UUID> = emptyList(),
    val syncKey: SyncKeyType? = null,
)
