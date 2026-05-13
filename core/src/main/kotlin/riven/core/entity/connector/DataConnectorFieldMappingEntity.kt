package cranium.core.entity.connector

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
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.models.connector.DataConnectorFieldMapping
import java.util.UUID

/**
 * JPA entity for column-level mapping state of a data connector connection
 * (Phase 3 plan 03-01, requirement PG-05 / MAP-08).
 *
 * One row per `(workspaceId, connectionId, tableName, columnName)`. The
 * three flag columns — `is_identifier`, `is_sync_cursor`, `is_primary_key` —
 * are conceptually independent per 03-CONTEXT.md: identifier is the
 * cross-source match key (Phase 5), sync cursor drives polling (Phase 4),
 * primary key drives per-record upsert fallback.
 *
 * `@SQLRestriction` on the concrete entity for the Phase 2 lesson reason.
 */
@Entity
@Table(
    name = "connector_field_mappings",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_connector_field_mappings_ws_conn_table_col",
            columnNames = ["workspace_id", "connection_id", "table_name", "column_name"],
        ),
    ],
)
@SQLRestriction("deleted = false")
class DataConnectorFieldMappingEntity(
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

    @Column(name = "column_name", nullable = false, length = 255)
    var columnName: String,

    @Column(name = "pg_data_type", nullable = false, length = 255)
    var pgDataType: String,

    @Column(name = "nullable", nullable = false)
    var nullable: Boolean,

    @Column(name = "is_primary_key", nullable = false)
    var isPrimaryKey: Boolean = false,

    @Column(name = "is_foreign_key", nullable = false)
    var isForeignKey: Boolean = false,

    @Column(name = "fk_target_table", nullable = true, length = 255)
    var fkTargetTable: String? = null,

    @Column(name = "fk_target_column", nullable = true, length = 255)
    var fkTargetColumn: String? = null,

    @Column(name = "attribute_name", nullable = false, length = 255)
    var attributeName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "schema_type", nullable = false, length = 50)
    var schemaType: SchemaType,

    @Column(name = "is_identifier", nullable = false)
    var isIdentifier: Boolean = false,

    @Column(name = "is_sync_cursor", nullable = false)
    var isSyncCursor: Boolean = false,

    @Column(name = "is_mapped", nullable = false)
    var isMapped: Boolean = false,

    @Column(name = "stale", nullable = false)
    var stale: Boolean = false,
) : AuditableSoftDeletableEntity() {

    fun toModel(): DataConnectorFieldMapping = DataConnectorFieldMapping(
        id = requireNotNull(id) {
            "DataConnectorFieldMappingEntity.id must not be null when mapping to model"
        },
        workspaceId = workspaceId,
        connectionId = connectionId,
        tableName = tableName,
        columnName = columnName,
        pgDataType = pgDataType,
        nullable = nullable,
        isPrimaryKey = isPrimaryKey,
        isForeignKey = isForeignKey,
        fkTargetTable = fkTargetTable,
        fkTargetColumn = fkTargetColumn,
        attributeName = attributeName,
        schemaType = schemaType,
        isIdentifier = isIdentifier,
        isSyncCursor = isSyncCursor,
        isMapped = isMapped,
        stale = stale,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
