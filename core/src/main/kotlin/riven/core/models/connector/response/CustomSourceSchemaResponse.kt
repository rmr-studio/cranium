package riven.core.models.connector.response

import riven.core.enums.common.validation.SchemaType
import riven.core.models.connector.CursorIndexWarning
import java.util.UUID

/**
 * GET /api/v1/custom-sources/connections/{id}/schema response.
 *
 * Combines the live Postgres introspection (tables + columns + FK metadata)
 * with any stored `CustomSourceTableMappingEntity` / `CustomSourceFieldMappingEntity`
 * rows for this connection and surfaces drift + cursor-index warnings.
 *
 * @property tables One entry per live table in the target schema. Stored
 *   mappings that no longer exist upstream are NOT returned — their stale
 *   rows are flagged in the mapping table as a side effect.
 */
data class CustomSourceSchemaResponse(
    val tables: List<TableSchemaResponse>,
)

data class TableSchemaResponse(
    val tableName: String,
    val schemaHash: String,
    val driftStatus: DriftStatus,
    val columns: List<ColumnSchemaResponse>,
    val cursorIndexWarning: CursorIndexWarning? = null,
    val detectedCursorColumn: String? = null,
    val primaryKeyColumn: String? = null,
    val existingEntityTypeId: UUID? = null,
)

data class ColumnSchemaResponse(
    val columnName: String,
    val pgDataType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    val isForeignKey: Boolean,
    val fkTarget: FkTargetRef? = null,
    val existingMapping: ExistingMappingRef? = null,
    val suggestedSchemaType: SchemaType,
    val autoDetectedCursor: Boolean,
)

data class FkTargetRef(
    val table: String,
    val column: String,
)

data class ExistingMappingRef(
    val attributeName: String,
    val schemaType: SchemaType,
    val isIdentifier: Boolean,
    val isSyncCursor: Boolean,
    val isMapped: Boolean,
)

enum class DriftStatus {
    NEW,
    CLEAN,
    DRIFTED,
}
