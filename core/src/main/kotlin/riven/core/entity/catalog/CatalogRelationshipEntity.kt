package cranium.core.entity.catalog

import jakarta.persistence.*
import org.hibernate.annotations.UpdateTimestamp
import cranium.core.enums.common.icon.IconColour
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.models.catalog.CatalogRelationshipModel
import cranium.core.models.catalog.CatalogRelationshipTargetRuleModel
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for catalog relationship definitions.
 *
 * Defines relationships between entity types within a manifest.
 * Uses VARCHAR source_entity_type_key (not UUID FK) since catalog
 * attributes use string keys resolved at load time.
 */
@Entity
@Table(
    name = "catalog_relationships",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["manifest_id", "key"])
    ]
)
data class CatalogRelationshipEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "manifest_id", nullable = false, columnDefinition = "uuid")
    val manifestId: UUID,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "source_entity_type_key", nullable = false)
    val sourceEntityTypeKey: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    val iconType: IconType = IconType.LINK,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_colour", nullable = false)
    val iconColour: IconColour = IconColour.NEUTRAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "cardinality_default", nullable = false)
    val cardinalityDefault: EntityRelationshipCardinality,

    @Column(name = "protected", nullable = false)
    val `protected`: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {

    fun toModel(targetRules: List<CatalogRelationshipTargetRuleModel>) = CatalogRelationshipModel(
        id = id!!,
        key = key,
        sourceEntityTypeKey = sourceEntityTypeKey,
        name = name,
        iconType = iconType,
        iconColour = iconColour,
        cardinalityDefault = cardinalityDefault,
        `protected` = `protected`,
        targetRules = targetRules
    )
}
