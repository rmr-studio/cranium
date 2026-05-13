package cranium.core.models.response.entity

import cranium.core.enums.integration.SourceType
import java.time.ZonedDateTime
import java.util.*

data class RelationshipResponse(
    val id: UUID,
    val sourceEntityId: UUID,
    val targetEntityId: UUID,
    val definitionId: UUID,
    val definitionName: String,
    val semanticContext: String?,
    val linkSource: SourceType,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
)
