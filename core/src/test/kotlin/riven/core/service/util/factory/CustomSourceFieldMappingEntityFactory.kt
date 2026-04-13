package riven.core.service.util.factory

import riven.core.entity.connector.CustomSourceFieldMappingEntity
import riven.core.enums.common.validation.SchemaType
import java.util.UUID

/** Test factory for [CustomSourceFieldMappingEntity] (Phase 3). */
object CustomSourceFieldMappingEntityFactory {

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
    ): CustomSourceFieldMappingEntity = CustomSourceFieldMappingEntity(
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
