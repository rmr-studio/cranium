package riven.core.entity.connector

import riven.core.enums.common.validation.SchemaType
import java.util.UUID

/**
 * Wave-0 shell for the Phase 3 custom-source field-mapping entity.
 *
 * Populated with full JPA annotations, SQL DDL, repository, and `toModel()`
 * by plan 03-01. Shape matches the field list declared in plan 03-00's
 * `<interfaces>` block so the corresponding test factory compiles today and
 * downstream plans can flesh out persistence without touching call sites.
 */
class CustomSourceFieldMappingEntity(
    val id: UUID? = null,
    val workspaceId: UUID,
    val connectionId: UUID,
    val tableName: String,
    val columnName: String,
    val pgDataType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean = false,
    val isForeignKey: Boolean = false,
    val fkTargetTable: String? = null,
    val fkTargetColumn: String? = null,
    val attributeName: String,
    val schemaType: SchemaType,
    val isIdentifier: Boolean = false,
    val isSyncCursor: Boolean = false,
    val isMapped: Boolean = true,
    val stale: Boolean = false,
)
