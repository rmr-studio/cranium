package riven.core.models.connector

import riven.core.enums.common.validation.SchemaType
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Response DTO for a custom-source field (column) mapping (Phase 3 plan 03-01).
 *
 * One row per `(workspaceId, connectionId, tableName, columnName)`. Carries
 * the per-column state that drives Phase 4 sync behavior (isSyncCursor,
 * isIdentifier, stale) and Phase 5 identity resolution (isIdentifier).
 */
data class CustomSourceFieldMapping(
    val id: UUID,
    val workspaceId: UUID,
    val connectionId: UUID,
    val tableName: String,
    val columnName: String,
    val pgDataType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean,
    val isForeignKey: Boolean,
    val fkTargetTable: String?,
    val fkTargetColumn: String?,
    val attributeName: String,
    val schemaType: SchemaType,
    val isIdentifier: Boolean,
    val isSyncCursor: Boolean,
    val isMapped: Boolean,
    val stale: Boolean,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
)
