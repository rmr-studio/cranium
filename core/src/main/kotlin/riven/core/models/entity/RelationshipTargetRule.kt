package cranium.core.models.entity

import cranium.core.enums.entity.EntityRelationshipCardinality
import java.time.ZonedDateTime
import java.util.*

data class RelationshipTargetRule(
    val id: UUID,
    val relationshipDefinitionId: UUID,
    val targetEntityTypeId: UUID,
    val cardinalityOverride: EntityRelationshipCardinality?,
    val inverseName: String,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
)
