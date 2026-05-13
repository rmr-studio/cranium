package cranium.core.models.integration

import cranium.core.enums.integration.SyncStatus
import java.util.*

data class IntegrationSyncState(
    val id: UUID,
    val integrationConnectionId: UUID,
    val entityTypeId: UUID,
    val status: SyncStatus,
    val lastCursor: String?,
    val consecutiveFailureCount: Int,
    val lastErrorMessage: String?,
    val lastRecordsSynced: Int?,
    val lastRecordsFailed: Int?,
    val lastPipelineStep: String?,
    val projectionResult: Map<String, Any>?
)
