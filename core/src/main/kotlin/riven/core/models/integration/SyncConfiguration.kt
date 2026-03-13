package riven.core.models.integration

import riven.core.enums.integration.SyncScope

data class SyncConfiguration(
    val syncScope: SyncScope = SyncScope.ALL,
    val syncWindowMonths: Int? = null
)
