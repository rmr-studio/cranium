package riven.core.entity.entity

import jakarta.persistence.*
import riven.core.entity.util.AuditableEntity
import java.util.*

/**
 * Exclusion records are type-level configuration (like target rules), not user data,
 * so they use hard-delete rather than soft-delete. They are removed when a type
 * re-enables a relationship or when the parent definition is deleted.
 */
@Entity
@Table(
    name = "relationship_definition_exclusions",
    indexes = [
        Index(name = "idx_excl_entity_type", columnList = "entity_type_id"),
        Index(name = "idx_excl_definition", columnList = "relationship_definition_id"),
    ]
)
data class RelationshipDefinitionExclusionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "relationship_definition_id", nullable = false, columnDefinition = "uuid")
    val relationshipDefinitionId: UUID,

    @Column(name = "entity_type_id", nullable = false, columnDefinition = "uuid")
    val entityTypeId: UUID,
) : AuditableEntity()
