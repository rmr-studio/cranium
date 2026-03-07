package riven.core.models.entity

import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.SystemRelationshipType
import riven.core.models.common.Icon
import java.time.ZonedDateTime
import java.util.*

data class RelationshipDefinition(
    val id: UUID,
    val workspaceId: UUID,
    val sourceEntityTypeId: UUID,
    val name: String,
    val icon: Icon,
    val allowPolymorphic: Boolean,
    val cardinalityDefault: EntityRelationshipCardinality,
    val protected: Boolean,
    val systemType: SystemRelationshipType? = null,
    val targetRules: List<RelationshipTargetRule> = emptyList(),
    val excludedEntityTypeIds: List<UUID> = emptyList(),
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
)
