package riven.core.models.integration

import riven.core.enums.integration.SyncScope

data class SyncConfiguration(
    val syncScope: SyncScope = SyncScope.ALL,
    val syncWindowMonths: Int? = null
) {
    init {
        when (syncScope) {
            SyncScope.ALL -> require(syncWindowMonths == null) {
                "syncWindowMonths must be null when syncScope is ALL"
            }
            SyncScope.RECENT -> {
                requireNotNull(syncWindowMonths) { "syncWindowMonths is required when syncScope is RECENT" }
                require(syncWindowMonths > 0) { "syncWindowMonths must be positive, got $syncWindowMonths" }
            }
        }
    }
}
