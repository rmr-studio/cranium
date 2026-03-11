package riven.core.models.entity.configuration

import java.util.*

data class ColumnConfiguration(
    val order: List<UUID> = emptyList(),
    val overrides: Map<UUID, ColumnOverride> = emptyMap()
)

data class ColumnOverride(
    val width: Int? = null,
    val visible: Boolean? = null
)
