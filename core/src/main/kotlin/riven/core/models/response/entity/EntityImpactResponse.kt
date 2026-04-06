package riven.core.models.response.entity

import riven.core.models.entity.Entity
import java.util.*

data class DeleteEntityResponse(
    // If the delete operation impacted other entities (eg. due to relationship changes), return them here grouped by EntityType key
    val deletedCount: Int = 0,
    val updatedEntities: Map<UUID, List<Entity>>? = null,
)