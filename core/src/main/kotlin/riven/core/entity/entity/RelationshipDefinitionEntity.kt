package riven.core.entity.entity

import jakarta.persistence.*
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.SystemRelationshipType
import riven.core.models.common.Icon
import riven.core.models.entity.RelationshipDefinition
import riven.core.models.entity.RelationshipTargetRule
import java.util.*

@Entity
@Table(
    name = "relationship_definitions",
    indexes = [
        Index(name = "idx_rel_def_workspace_source", columnList = "workspace_id, source_entity_type_id"),
    ]
)
data class RelationshipDefinitionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "source_entity_type_id", nullable = false, columnDefinition = "uuid")
    val sourceEntityTypeId: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    var iconType: IconType = IconType.LINK,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_value", nullable = false)
    var iconColour: IconColour = IconColour.NEUTRAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "cardinality_default", nullable = false)
    var cardinalityDefault: EntityRelationshipCardinality,

    @Column(name = "protected", nullable = false)
    val protected: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "system_type")
    val systemType: SystemRelationshipType? = null,
) : AuditableSoftDeletableEntity() {

    fun toModel(
        targetRules: List<RelationshipTargetRule> = emptyList(),
    ): RelationshipDefinition {
        val id = requireNotNull(this.id) { "RelationshipDefinitionEntity ID cannot be null" }
        return RelationshipDefinition(
            id = id,
            workspaceId = this.workspaceId,
            sourceEntityTypeId = this.sourceEntityTypeId,
            name = this.name,
            icon = Icon(this.iconType, this.iconColour),
            cardinalityDefault = this.cardinalityDefault,
            protected = this.protected,
            systemType = this.systemType,
            targetRules = targetRules,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            createdBy = this.createdBy,
            updatedBy = this.updatedBy,
        )
    }
}
