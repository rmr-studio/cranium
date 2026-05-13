package cranium.core.entity.entity

import jakarta.persistence.*
import cranium.core.entity.util.AuditableEntity
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.models.entity.RelationshipTargetRule
import java.util.*

/**
 * Target rules are type-level configuration, not user data, so they use hard-delete rather than soft-delete.
 * They are recreated via diff when the parent definition is updated, and are cascade-deleted when the definition is deleted.
 */
@Entity
@Table(
    name = "relationship_target_rules",
    indexes = [
        Index(name = "idx_target_rule_def", columnList = "relationship_definition_id"),
        Index(name = "idx_target_rule_type", columnList = "target_entity_type_id"),
    ]
)
data class RelationshipTargetRuleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "relationship_definition_id", nullable = false, columnDefinition = "uuid")
    val relationshipDefinitionId: UUID,

    @Column(name = "target_entity_type_id", nullable = false, columnDefinition = "uuid")
    val targetEntityTypeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "cardinality_override", nullable = true)
    val cardinalityOverride: EntityRelationshipCardinality? = null,

    @Column(name = "inverse_name", nullable = false)
    var inverseName: String,
) : AuditableEntity() {

    fun toModel(): RelationshipTargetRule {
        val id = requireNotNull(this.id) { "RelationshipTargetRuleEntity ID cannot be null" }
        return RelationshipTargetRule(
            id = id,
            relationshipDefinitionId = this.relationshipDefinitionId,
            targetEntityTypeId = this.targetEntityTypeId,
            cardinalityOverride = this.cardinalityOverride,
            inverseName = this.inverseName,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
