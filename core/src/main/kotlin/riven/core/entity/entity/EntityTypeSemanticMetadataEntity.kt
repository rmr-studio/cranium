package cranium.core.entity.entity

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticMetadataTargetType
import cranium.core.enums.identity.MatchSignalType
import cranium.core.enums.identity.MatchSignalTypeConverter
import cranium.core.models.entity.EntityTypeSemanticMetadata
import java.util.*

/**
 * JPA entity for semantic metadata records.
 *
 * One row per (entity_type_id, target_type, target_id) — enforced by the unique constraint.
 * Soft-delete is handled via AuditableSoftDeletableEntity and the @SQLRestriction filter.
 */
@Entity
@Table(
    name = "entity_type_semantic_metadata",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["entity_type_id", "target_type", "target_id"])
    ]
)
@SQLRestriction("deleted = false")
data class EntityTypeSemanticMetadataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "entity_type_id", nullable = false, columnDefinition = "uuid")
    val entityTypeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    val targetType: SemanticMetadataTargetType,

    @Column(name = "target_id", nullable = false, columnDefinition = "uuid")
    val targetId: UUID,

    @Column(name = "definition", nullable = true)
    var definition: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = true)
    var classification: SemanticAttributeClassification? = null,

    @Convert(converter = MatchSignalTypeConverter::class)
    @Column(name = "signal_type", nullable = true)
    var signalType: MatchSignalType? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "tags", columnDefinition = "jsonb", nullable = false)
    var tags: List<String> = emptyList(),
) : AuditableSoftDeletableEntity() {

    /**
     * Convert this entity to a domain model.
     */
    fun toModel(): EntityTypeSemanticMetadata {
        val id = requireNotNull(this.id) { "EntityTypeSemanticMetadataEntity ID cannot be null" }
        return EntityTypeSemanticMetadata(
            id = id,
            workspaceId = this.workspaceId,
            entityTypeId = this.entityTypeId,
            targetType = this.targetType,
            targetId = this.targetId,
            definition = this.definition,
            classification = this.classification,
            signalType = this.signalType,
            tags = this.tags,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            createdBy = this.createdBy,
            updatedBy = this.updatedBy
        )
    }
}
