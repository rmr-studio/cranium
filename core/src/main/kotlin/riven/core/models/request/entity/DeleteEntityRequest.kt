package cranium.core.models.request.entity

import cranium.core.enums.entity.EntitySelectType
import cranium.core.models.entity.query.filter.QueryFilter
import cranium.core.validation.entity.ValidDeleteEntityRequest
import java.util.*

@ValidDeleteEntityRequest
data class DeleteEntityRequest(
    val type: EntitySelectType,
    val entityTypeId: UUID? = null,
    val entityIds: List<UUID>? = null,
    val filter: QueryFilter? = null,
    val excludeIds: List<UUID>? = null,
)
