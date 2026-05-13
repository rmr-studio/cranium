package cranium.core.models.request.entity

import cranium.core.models.common.Icon
import cranium.core.models.entity.payload.EntityAttributeRequest
import java.util.*

data class SaveEntityRequest(
    val id: UUID? = null,
    val payload: Map<UUID, EntityAttributeRequest>,
    val icon: Icon? = null,
)