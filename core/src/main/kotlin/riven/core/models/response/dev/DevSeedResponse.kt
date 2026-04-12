package riven.core.models.response.dev

import java.util.*

data class DevSeedResponse(
    val alreadySeeded: Boolean = false,
    val templateKey: String? = null,
    val entitiesCreated: Int = 0,
    val relationshipsCreated: Int = 0,
    val details: Map<String, EntityTypeSeedDetail> = emptyMap(),
)

data class EntityTypeSeedDetail(
    val entityTypeKey: String,
    val entityTypeId: UUID,
    val entitiesCreated: Int,
    val relationshipsCreated: Int,
)
