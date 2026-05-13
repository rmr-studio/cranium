package cranium.core.models.entity

import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.entity.SystemRelationshipType
import cranium.core.models.common.Icon
import java.time.ZonedDateTime
import java.util.*

data class RelationshipDefinition(
    val id: UUID,
    val workspaceId: UUID,
    val sourceEntityTypeId: UUID,
    val name: String,
    val icon: Icon,
    val cardinalityDefault: EntityRelationshipCardinality,
    val protected: Boolean,
    val systemType: SystemRelationshipType? = null,
    val targetRules: List<RelationshipTargetRule> = emptyList(),
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
    val createdBy: UUID?,
    val updatedBy: UUID?,
) {
    /** Polymorphic relationships are only supported for system-managed definitions (e.g. SYSTEM_CONNECTION). */
    val isPolymorphic: Boolean get() = systemType != null
}
