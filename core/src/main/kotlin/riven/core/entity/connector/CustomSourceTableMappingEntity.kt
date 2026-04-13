package riven.core.entity.connector

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.connector.CustomSourceTableMapping
import java.time.ZonedDateTime
import java.util.UUID

/**
 * JPA entity for table-level mapping state of a custom source connection
 * (Phase 3 plan 03-01, requirement PG-02 / MAP-02).
 *
 * One row per `(workspaceId, connectionId, tableName)`. Holds the
 * user-selected [LifecycleDomain] / [SemanticGroup], the generated
 * [EntityTypeEntity] id once the mapping is Saved, the schema hash used for
 * drift detection, and the `published` flag that gates sync (Phase 4 only
 * syncs `published = true`).
 *
 * Extends [AuditableSoftDeletableEntity]; `@SQLRestriction("deleted = false")`
 * is declared on the concrete entity because Hibernate 6 does not reliably
 * propagate mapped-superclass SQLRestriction to derived queries (Phase 2 02-01
 * lesson).
 */
@Entity
@Table(
    name = "connector_table_mappings",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_connector_table_mappings_ws_conn_table",
            columnNames = ["workspace_id", "connection_id", "table_name"],
        ),
    ],
)
@SQLRestriction("deleted = false")
class CustomSourceTableMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "connection_id", nullable = false, columnDefinition = "uuid")
    val connectionId: UUID,

    @Column(name = "table_name", nullable = false, length = 255)
    var tableName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_domain", nullable = false, length = 50)
    var lifecycleDomain: LifecycleDomain = LifecycleDomain.UNCATEGORIZED,

    @Enumerated(EnumType.STRING)
    @Column(name = "semantic_group", nullable = false, length = 50)
    var semanticGroup: SemanticGroup = SemanticGroup.UNCATEGORIZED,

    @Column(name = "entity_type_id", nullable = true, columnDefinition = "uuid")
    var entityTypeId: UUID? = null,

    @Column(name = "schema_hash", nullable = false, length = 128)
    var schemaHash: String,

    @Column(name = "last_introspected_at", nullable = false)
    var lastIntrospectedAt: ZonedDateTime,

    @Column(name = "published", nullable = false)
    var published: Boolean = false,
) : AuditableSoftDeletableEntity() {

    fun toModel(): CustomSourceTableMapping = CustomSourceTableMapping(
        id = requireNotNull(id) {
            "CustomSourceTableMappingEntity.id must not be null when mapping to model"
        },
        workspaceId = workspaceId,
        connectionId = connectionId,
        tableName = tableName,
        lifecycleDomain = lifecycleDomain,
        semanticGroup = semanticGroup,
        entityTypeId = entityTypeId,
        schemaHash = schemaHash,
        lastIntrospectedAt = lastIntrospectedAt,
        published = published,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
