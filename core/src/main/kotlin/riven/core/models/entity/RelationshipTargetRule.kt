package riven.core.models.entity

import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticGroup
import java.time.ZonedDateTime
import java.util.*

data class RelationshipTargetRule(
    val id: UUID,
    val relationshipDefinitionId: UUID,
    val targetEntityTypeId: UUID?,
    val semanticTypeConstraint: SemanticGroup?,
    val cardinalityOverride: EntityRelationshipCardinality?,
    val inverseVisible: Boolean,
    val inverseName: String?,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
)
