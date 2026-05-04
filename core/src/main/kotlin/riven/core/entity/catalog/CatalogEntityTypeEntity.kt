package riven.core.entity.catalog

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.Type
import org.hibernate.annotations.UpdateTimestamp
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.EntityTypeRole
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.catalog.CatalogEntityTypeModel
import riven.core.models.catalog.CatalogSemanticMetadataModel
import riven.core.models.catalog.ConnotationSignals
import java.time.ZonedDateTime
import java.util.*

/**
 * JPA entity for catalog entity type definitions.
 *
 * Each manifest can define multiple entity types. Schema and columns
 * are stored as raw JSONB since catalog attributes use string keys.
 */
@Entity
@Table(
    name = "catalog_entity_types",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["manifest_id", "key"])
    ]
)
data class CatalogEntityTypeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "manifest_id", nullable = false, columnDefinition = "uuid")
    val manifestId: UUID,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "display_name_singular", nullable = false)
    val displayNameSingular: String,

    @Column(name = "display_name_plural", nullable = false)
    val displayNamePlural: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    val iconType: IconType = IconType.CIRCLE_DASHED,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_colour", nullable = false)
    val iconColour: IconColour = IconColour.NEUTRAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_group", nullable = false)
    val semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_domain", nullable = false)
    val lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,

    @Enumerated(EnumType.STRING)
    @Column(name = "surface_role", nullable = false)
    val role: EntityTypeRole = EntityTypeRole.CATALOG,

    @Column(name = "identifier_key")
    val identifierKey: String? = null,

    @Column(name = "readonly", nullable = false)
    val readonly: Boolean = false,

    @Type(JsonBinaryType::class)
    @Column(name = "schema", columnDefinition = "jsonb", nullable = false)
    var schema: Map<String, Any> = emptyMap(),

    @Type(JsonBinaryType::class)
    @Column(name = "columns", columnDefinition = "jsonb", nullable = true)
    var columns: List<Map<String, Any>>? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "connotation_signals", columnDefinition = "jsonb", nullable = true)
    var connotationSignals: ConnotationSignals? = null,

    @Column(name = "schema_hash", length = 64)
    var schemaHash: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {

    fun toModel(semanticMetadata: List<CatalogSemanticMetadataModel>) = CatalogEntityTypeModel(
        id = requireNotNull(id) { "CatalogEntityTypeEntity.id must not be null when converting to CatalogEntityTypeModel" },
        manifestId = manifestId,
        key = key,
        displayNameSingular = displayNameSingular,
        displayNamePlural = displayNamePlural,
        iconType = iconType,
        iconColour = iconColour,
        semanticGroup = semanticGroup,
        lifecycleDomain = lifecycleDomain,
        role = role,
        identifierKey = identifierKey,
        readonly = readonly,
        schema = schema,
        columns = columns,
        connotationSignals = connotationSignals,
        schemaHash = schemaHash,
        semanticMetadata = semanticMetadata
    )
}
