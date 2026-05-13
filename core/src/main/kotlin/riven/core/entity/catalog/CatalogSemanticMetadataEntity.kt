package cranium.core.entity.catalog

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import org.hibernate.annotations.UpdateTimestamp
import cranium.core.enums.entity.semantics.SemanticAttributeClassification
import cranium.core.enums.entity.semantics.SemanticMetadataTargetType
import cranium.core.models.catalog.CatalogSemanticMetadataModel
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for catalog semantic metadata.
 *
 * Stores semantic definitions, classifications, and tags for
 * catalog entity types, attributes, and relationships.
 * target_id is String (not UUID) to accommodate string attribute keys.
 */
@Entity
@Table(
    name = "catalog_semantic_metadata",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["catalog_entity_type_id", "target_type", "target_id"])
    ]
)
data class CatalogSemanticMetadataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "catalog_entity_type_id", nullable = false, columnDefinition = "uuid")
    val catalogEntityTypeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    val targetType: SemanticMetadataTargetType,

    @Column(name = "target_id", nullable = false)
    val targetId: String,

    @Column(name = "definition")
    val definition: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "classification")
    val classification: SemanticAttributeClassification? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "tags", columnDefinition = "jsonb", nullable = false)
    var tags: List<String> = emptyList(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {

    fun toModel() = CatalogSemanticMetadataModel(
        id = id!!,
        targetType = targetType,
        targetId = targetId,
        definition = definition,
        classification = classification,
        tags = tags
    )
}
