package cranium.core.service.util.factory

import cranium.core.entity.connector.DataConnectorFieldMappingEntity
import cranium.core.enums.common.validation.SchemaType
import java.util.UUID

/** Test factory for [DataConnectorFieldMappingEntity] (Phase 3). */
object DataConnectorFieldMappingEntityFactory {

    fun create(
        workspaceId: UUID = UUID.randomUUID(),
        connectionId: UUID = UUID.randomUUID(),
        tableName: String = "customers",
        columnName: String = "email",
        pgDataType: String = "text",
        nullable: Boolean = false,
        isPrimaryKey: Boolean = false,
        isForeignKey: Boolean = false,
        fkTargetTable: String? = null,
        fkTargetColumn: String? = null,
        attributeName: String = "email",
        schemaType: SchemaType = SchemaType.EMAIL,
        isIdentifier: Boolean = false,
        isSyncCursor: Boolean = false,
        isMapped: Boolean = true,
        stale: Boolean = false,
    ): DataConnectorFieldMappingEntity = DataConnectorFieldMappingEntity(
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
    )
}
