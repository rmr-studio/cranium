package riven.core.entity.catalog

import jakarta.persistence.*
import org.hibernate.annotations.UpdateTimestamp
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.catalog.CatalogRelationshipTargetRuleModel
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for catalog relationship target rules.
 *
 * Defines target constraints for polymorphic or multi-target relationships.
 * Child of CatalogRelationshipEntity.
 */
@Entity
@Table(name = "catalog_relationship_target_rules")
data class CatalogRelationshipTargetRuleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "catalog_relationship_id", nullable = false, columnDefinition = "uuid")
    val catalogRelationshipId: UUID,

    @Column(name = "target_entity_type_key", nullable = false)
    val targetEntityTypeKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_type_constraint")
    val semanticTypeConstraint: SemanticGroup? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "cardinality_override")
    val cardinalityOverride: EntityRelationshipCardinality? = null,

    @Column(name = "inverse_visible", nullable = false)
    val inverseVisible: Boolean = false,

    @Column(name = "inverse_name")
    val inverseName: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {

    fun toModel() = CatalogRelationshipTargetRuleModel(
        id = id!!,
        targetEntityTypeKey = targetEntityTypeKey,
        semanticTypeConstraint = semanticTypeConstraint,
        cardinalityOverride = cardinalityOverride,
        inverseVisible = inverseVisible,
        inverseName = inverseName
    )
}
