package riven.core.entity.entity

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.integration.SourceType
import riven.core.models.entity.EntityRelationship
import java.util.*

/**
 * JPA entity for relationships between entities.
 */
@Entity
@Table(
    name = "entity_relationships",
    indexes = [
        Index(name = "idx_entity_relationships_source", columnList = "workspace_id, source_entity_id"),
        Index(name = "idx_entity_relationships_target", columnList = "workspace_id, target_entity_id"),
        Index(name = "idx_entity_relationships_definition", columnList = "relationship_definition_id"),
    ]
)
@SQLRestriction("deleted = false")
data class EntityRelationshipEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "source_entity_id", nullable = false, columnDefinition = "uuid")
    val sourceId: UUID,

    @Column(name = "target_entity_id", nullable = false, columnDefinition = "uuid")
    val targetId: UUID,

    @Column(name = "relationship_definition_id", nullable = false, columnDefinition = "uuid")
    val definitionId: UUID,

    @Column(name = "semantic_context")
    val semanticContext: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "link_source", nullable = false)
    val linkSource: SourceType = SourceType.USER_CREATED,

) : AuditableSoftDeletableEntity() {

    /**
     * Convert this entity to a domain model.
     */
    fun toModel(audit: Boolean = false): EntityRelationship {
        val id = requireNotNull(this.id) { "EntityRelationshipEntity ID cannot be null" }
        return EntityRelationship(
            id = id,
            workspaceId = this.workspaceId,
            definitionId = this.definitionId,
            sourceEntityId = this.sourceId,
            targetEntityId = this.targetId,
            semanticContext = this.semanticContext,
            linkSource = this.linkSource,
            createdAt = if (audit) this.createdAt else null,
            updatedAt = if (audit) this.updatedAt else null,
            createdBy = if (audit) this.createdBy else null,
            updatedBy = if (audit) this.updatedBy else null
        )
    }
}
