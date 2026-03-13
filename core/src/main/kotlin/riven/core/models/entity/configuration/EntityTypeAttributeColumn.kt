package riven.core.models.entity.configuration

import riven.core.enums.entity.EntityPropertyType
import java.util.*

data class EntityTypeAttributeColumn(
    val key: UUID,
    val type: EntityPropertyType,
    val visible: Boolean = true,
    val width: Int = 150
)