package cranium.core.models.entity.configuration

import cranium.core.enums.entity.EntityPropertyType
import java.util.*

data class EntityTypeAttributeColumn(
    val key: UUID,
    val type: EntityPropertyType,
    val visible: Boolean = true,
    val width: Int = 150
)