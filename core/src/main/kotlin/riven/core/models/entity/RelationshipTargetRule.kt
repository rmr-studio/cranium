package riven.core.models.entity

import riven.core.enums.entity.EntityRelationshipCardinality
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
