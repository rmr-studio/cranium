package riven.core.entity.catalog

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import org.hibernate.annotations.UpdateTimestamp
import riven.core.enums.catalog.ManifestType
import riven.core.models.catalog.*
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for the manifest catalog (global catalog).
 *
 * Stores metadata about loaded manifests (models, templates, integrations).
 * Does NOT extend AuditableEntity — global catalog, not user-owned.
 * No soft-delete — catalog entries are permanent.
 */
@Entity
@Table(
    name = "manifest_catalog",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["key", "manifest_type"])
    ]
)
data class ManifestCatalogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "description")
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "manifest_type", nullable = false)
    val manifestType: ManifestType,

    @Column(name = "manifest_version")
    val manifestVersion: String? = null,

    @Column(name = "last_loaded_at")
    var lastLoadedAt: ZonedDateTime? = null,

    @Column(name = "stale", nullable = false)
    var stale: Boolean = false,

    @Column(name = "content_hash", length = 64)
    var contentHash: String? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "template_keys", columnDefinition = "jsonb")
    val templateKeys: List<String>? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {

    fun toSummary(entityTypeCount: Int) = ManifestSummary(
        id = id!!,
        key = key,
        name = name,
        description = description,
        manifestVersion = manifestVersion,
        entityTypeCount = entityTypeCount
    )

    fun toDetail(
        entityTypes: List<CatalogEntityTypeModel>,
        relationships: List<CatalogRelationshipModel>,
        fieldMappings: List<CatalogFieldMappingModel>
    ) = ManifestDetail(
        id = id!!,
        key = key,
        name = name,
        description = description,
        manifestType = manifestType,
        manifestVersion = manifestVersion,
        entityTypes = entityTypes,
        relationships = relationships,
        fieldMappings = fieldMappings
    )

}
