package riven.core.entity.entity

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.models.common.Icon
import riven.core.models.common.display.DisplayName
import riven.core.models.entity.EntityType
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.configuration.ColumnConfiguration
import java.util.*

/**
 * JPA entity for entity types.
 *
 * Key difference from BlockTypeEntity: EntityTypes are MUTABLE.
 * Updates modify the existing row rather than creating new versions.
 */
@Entity
@Table(
    name = "entity_types",
    indexes = [
        Index(columnList = "workspace_id", name = "idx_entity_types_workspace_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workspace_id", "key"])
    ]
)
@SQLRestriction("deleted = false")
data class EntityTypeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "key", nullable = false, updatable = false)
    val key: String,

    @Column(name = "display_name_singular", nullable = false)
    var displayNameSingular: String,

    @Column(name = "display_name_plural", nullable = false)
    var displayNamePlural: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    var iconType: IconType = IconType.CIRCLE_DASHED,

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_colour", nullable = false)
    var iconColour: IconColour = IconColour.NEUTRAL,

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_group", nullable = false)
    var semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_domain", nullable = false)
    var lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    val sourceType: SourceType = SourceType.USER_CREATED,

    @Column(name = "source_integration_id", columnDefinition = "uuid")
    val sourceIntegrationId: UUID? = null,

    @Column(name = "readonly", nullable = false)
    val readonly: Boolean = false,

    @Column(name = "source_schema_hash", length = 64)
    var sourceSchemaHash: String? = null,

    @Column(name = "pending_schema_update", nullable = false)
    var pendingSchemaUpdate: Boolean = false,

    @Column(name = "source_manifest_id", columnDefinition = "uuid")
    var sourceManifestId: UUID? = null,

    @Column(name = "identifier_key", nullable = false)
    val identifierKey: UUID,

    @Column(name = "workspace_id", columnDefinition = "uuid")
    val workspaceId: UUID? = null,

    @Column(name = "protected", nullable = false)
    val protected: Boolean = false,

    @Column(name = "version", nullable = false, columnDefinition = "integer default 1")
    var version: Int = 1,

    @Type(JsonBinaryType::class)
    @Column(name = "schema", columnDefinition = "jsonb", nullable = false)
    var schema: EntityTypeSchema,

    @Type(JsonBinaryType::class)
    @Column(name = "column_configuration", columnDefinition = "jsonb", nullable = true)
    var columnConfiguration: ColumnConfiguration? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "attribute_key_mapping", columnDefinition = "jsonb")
    var attributeKeyMapping: Map<String, String>? = null,

    // Number of entities of this type, calculated via trigger on entities table
    @Column(name = "count", nullable = false)
    var entitiesCount: Long = 0L,
) : AuditableSoftDeletableEntity() {

    /**
     * Convert this entity to a domain model.
     */
    fun toModel(): EntityType {
        val id = requireNotNull(this.id) { "EntityTypeEntity ID cannot be null" }
        return EntityType(
            id = id,
            key = this.key,
            version = this.version,
            name = DisplayName(this.displayNameSingular, this.displayNamePlural),
            icon = Icon(this.iconType, this.iconColour),
            identifierKey = this.identifierKey,
            semanticGroup = this.semanticGroup,
            lifecycleDomain = this.lifecycleDomain,
            sourceType = this.sourceType,
            sourceIntegrationId = this.sourceIntegrationId,
            readonly = this.readonly,
            workspaceId = this.workspaceId,
            protected = this.protected,
            schema = this.schema,
            columnConfiguration = this.columnConfiguration,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            createdBy = this.createdBy,
            updatedBy = this.updatedBy
        )
    }
}
