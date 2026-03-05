package riven.core.entity.catalog

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import org.hibernate.annotations.UpdateTimestamp
import riven.core.models.catalog.CatalogFieldMappingModel
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for catalog field mappings.
 *
 * Maps external provider fields to entity type attributes
 * for integration manifests.
 */
@Entity
@Table(
    name = "catalog_field_mappings",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["manifest_id", "entity_type_key"])
    ]
)
data class CatalogFieldMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "manifest_id", nullable = false, columnDefinition = "uuid")
    val manifestId: UUID,

    @Column(name = "entity_type_key", nullable = false)
    val entityTypeKey: String,

    @Type(JsonBinaryType::class)
    @Column(name = "mappings", columnDefinition = "jsonb", nullable = false)
    var mappings: Map<String, Any> = emptyMap(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {

    fun toModel() = CatalogFieldMappingModel(
        id = id!!,
        entityTypeKey = entityTypeKey,
        mappings = mappings
    )
}
