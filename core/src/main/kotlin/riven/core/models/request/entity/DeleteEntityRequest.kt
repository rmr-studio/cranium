package riven.core.models.request.entity

import riven.core.enums.entity.EntitySelectType
import riven.core.models.entity.query.filter.QueryFilter
import riven.core.validation.entity.ValidDeleteEntityRequest
import java.util.*

@ValidDeleteEntityRequest
data class DeleteEntityRequest(
    val type: EntitySelectType,
    val entityTypeId: UUID? = null,
    val entityIds: List<UUID>? = null,
    val filter: QueryFilter? = null,
    val excludeIds: List<UUID>? = null,
)
