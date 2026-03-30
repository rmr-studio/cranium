package riven.core.entity.integration

import jakarta.persistence.*
import riven.core.models.integration.projection.ProjectionRule
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for entity type projection rules.
 *
 * Maps source entity types (integration) to target entity types (core lifecycle).
 * System-managed — does NOT extend AuditableEntity or implement SoftDeletable.
 * Installed automatically from core model projectionAccepts during materialization.
 */
@Entity
@Table(
    name = "entity_type_projection_rules",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_projection_rule_source_target",
            columnNames = ["workspace_id", "source_entity_type_id", "target_entity_type_id"]
        )
    ]
)
data class ProjectionRuleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", columnDefinition = "uuid")
    val workspaceId: UUID? = null,

    @Column(name = "source_entity_type_id", nullable = false, columnDefinition = "uuid")
    val sourceEntityTypeId: UUID,

    @Column(name = "target_entity_type_id", nullable = false, columnDefinition = "uuid")
    val targetEntityTypeId: UUID,

    @Column(name = "relationship_def_id", columnDefinition = "uuid")
    val relationshipDefId: UUID? = null,

    @Column(name = "auto_create", nullable = false)
    val autoCreate: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
) {

    fun toModel() = ProjectionRule(
        id = requireNotNull(id) { "ProjectionRuleEntity.id must not be null when mapping to model" },
        workspaceId = workspaceId,
        sourceEntityTypeId = sourceEntityTypeId,
        targetEntityTypeId = targetEntityTypeId,
        relationshipDefId = relationshipDefId,
        autoCreate = autoCreate,
    )
}
