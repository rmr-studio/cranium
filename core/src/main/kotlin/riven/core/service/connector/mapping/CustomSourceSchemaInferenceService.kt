package riven.core.service.connector.mapping

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.connector.CustomSourceFieldMappingEntity
import riven.core.entity.connector.CustomSourceTableMappingEntity
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.models.connector.CredentialPayload
import riven.core.models.connector.CursorIndexWarning
import riven.core.models.connector.response.ColumnSchemaResponse
import riven.core.models.connector.response.CustomSourceSchemaResponse
import riven.core.models.connector.response.DriftStatus
import riven.core.models.connector.response.ExistingMappingRef
import riven.core.models.connector.response.FkTargetRef
import riven.core.models.connector.response.TableSchemaResponse
import riven.core.models.ingestion.adapter.ColumnSchema
import riven.core.models.ingestion.adapter.TableSchema
import riven.core.repository.connector.CustomSourceConnectionRepository
import riven.core.repository.connector.CustomSourceFieldMappingRepository
import riven.core.repository.connector.CustomSourceTableMappingRepository
import riven.core.service.connector.CredentialEncryptionService
import riven.core.service.connector.EncryptedCredentials
import riven.core.service.connector.postgres.ForeignKeyMetadata
import riven.core.service.connector.postgres.PgTypeMapper
import riven.core.service.connector.postgres.PostgresAdapter
import riven.core.service.connector.postgres.SchemaHasher
import riven.core.service.ingestion.adapter.PostgresCallContext
import riven.core.util.ServiceUtil.findOrThrow
import java.time.ZonedDateTime
import java.util.UUID

/**
 * GET /api/v1/custom-sources/connections/{id}/schema — merges live Postgres
 * introspection with stored mapping rows, computes per-table schema-hash drift,
 * surfaces FK metadata and cursor-index warnings.
 *
 * Workspace-scoped via `@PreAuthorize`. `@Transactional` because the GET
 * performs side-effect writes on drift: dropped columns (present in stored
 * mapping, absent in the fresh introspection) are flagged `stale = true`;
 * each table mapping's `schemaHash` + `lastIntrospectedAt` are also updated.
 */
@Service
class CustomSourceSchemaInferenceService(
    private val postgresAdapter: PostgresAdapter,
    private val encryptionService: CredentialEncryptionService,
    private val connectionRepository: CustomSourceConnectionRepository,
    private val tableMappingRepository: CustomSourceTableMappingRepository,
    private val fieldMappingRepository: CustomSourceFieldMappingRepository,
    private val cursorIndexProbe: CursorIndexProbe,
    private val objectMapper: ObjectMapper,
    @Suppress("unused") private val logger: KLogger,
) {

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getSchema(workspaceId: UUID, connectionId: UUID): CustomSourceSchemaResponse {
        // Resolve + decrypt credentials once — reused for the probe.
        val credentials = resolveCredentials(workspaceId, connectionId)

        // Live introspection (schema + FK metadata).
        val context = PostgresCallContext(workspaceId = workspaceId, connectionId = connectionId)
        val introspection = postgresAdapter.introspectWithFkMetadata(context)

        // Pre-load stored mappings (two queries, indexed by connection + table).
        val storedTables = tableMappingRepository.findByConnectionId(connectionId)
            .associateBy { it.tableName }
        val storedFields = fieldMappingRepository.findByConnectionId(connectionId)
            .groupBy { it.tableName }

        val tableResponses = introspection.schema.tables.map { liveTable ->
            buildTableResponse(
                workspaceId = workspaceId,
                connectionId = connectionId,
                liveTable = liveTable,
                foreignKeys = introspection.foreignKeys,
                storedTable = storedTables[liveTable.name],
                storedFields = storedFields[liveTable.name].orEmpty(),
                credentials = credentials,
                schema = context.schema,
            )
        }

        return CustomSourceSchemaResponse(tables = tableResponses)
    }

    // ------ Private helpers ------

    private fun resolveCredentials(workspaceId: UUID, connectionId: UUID): CredentialPayload {
        val entity = findOrThrow { connectionRepository.findByIdAndWorkspaceId(connectionId, workspaceId) }
        val decryptedJson = encryptionService.decrypt(
            EncryptedCredentials(entity.encryptedCredentials, entity.iv, entity.keyVersion),
        )
        return objectMapper.readValue(decryptedJson)
    }

    private fun buildTableResponse(
        workspaceId: UUID,
        connectionId: UUID,
        liveTable: TableSchema,
        foreignKeys: List<ForeignKeyMetadata>,
        storedTable: CustomSourceTableMappingEntity?,
        storedFields: List<CustomSourceFieldMappingEntity>,
        credentials: CredentialPayload,
        schema: String,
    ): TableSchemaResponse {
        val freshHash = SchemaHasher.compute(liveTable.name, liveTable.columns)
        val driftStatus = resolveDriftStatus(storedTable, storedFields, liveTable, freshHash)

        val storedByColumn = storedFields.associateBy { it.columnName }
        val fksForThisTable = foreignKeys.filter { it.sourceTable == liveTable.name && !it.isComposite }
        val primaryKeyColumn = storedFields.firstOrNull { it.isPrimaryKey }?.columnName
        val detectedCursor = autoDetectCursorColumn(liveTable.columns)

        val columnResponses = liveTable.columns.map { column ->
            buildColumnResponse(
                column = column,
                fks = fksForThisTable,
                stored = storedByColumn[column.name],
                isPrimaryKey = column.name == primaryKeyColumn,
                autoDetected = column.name == detectedCursor,
            )
        }

        // Drift side-effect: mark dropped columns stale.
        markDroppedColumnsStale(storedFields, liveTable.columns)

        // Refresh stored table-mapping hash + timestamp (but NOT `published`).
        storedTable?.let {
            it.schemaHash = freshHash
            it.lastIntrospectedAt = ZonedDateTime.now()
            tableMappingRepository.save(it)
        }

        // Cursor-index warning: probe the chosen-or-detected cursor column.
        val chosenCursor = storedFields.firstOrNull { it.isSyncCursor }?.columnName ?: detectedCursor
        val cursorIndexWarning = chosenCursor?.let {
            buildCursorIndexWarning(connectionId, credentials, schema, liveTable.name, it)
        }

        return TableSchemaResponse(
            tableName = liveTable.name,
            schemaHash = freshHash,
            driftStatus = driftStatus,
            columns = columnResponses,
            cursorIndexWarning = cursorIndexWarning,
            detectedCursorColumn = detectedCursor,
            primaryKeyColumn = primaryKeyColumn,
            existingEntityTypeId = storedTable?.entityTypeId,
        )
    }

    private fun buildColumnResponse(
        column: ColumnSchema,
        fks: List<ForeignKeyMetadata>,
        stored: CustomSourceFieldMappingEntity?,
        isPrimaryKey: Boolean,
        autoDetected: Boolean,
    ): ColumnSchemaResponse {
        val matchingFk = fks.firstOrNull { it.sourceColumn == column.name }
        return ColumnSchemaResponse(
            columnName = column.name,
            pgDataType = column.typeLiteral,
            nullable = column.nullable,
            isPrimaryKey = isPrimaryKey || (stored?.isPrimaryKey ?: false),
            isForeignKey = matchingFk != null || (stored?.isForeignKey ?: false),
            fkTarget = matchingFk?.let { FkTargetRef(table = it.targetTable, column = it.targetColumn) }
                ?: stored?.let {
                    if (it.fkTargetTable != null && it.fkTargetColumn != null) {
                        FkTargetRef(it.fkTargetTable!!, it.fkTargetColumn!!)
                    } else null
                },
            existingMapping = stored?.let {
                ExistingMappingRef(
                    attributeName = it.attributeName,
                    schemaType = it.schemaType,
                    isIdentifier = it.isIdentifier,
                    isSyncCursor = it.isSyncCursor,
                    isMapped = it.isMapped,
                )
            },
            suggestedSchemaType = PgTypeMapper.toSchemaType(
                pgType = column.typeLiteral,
                isPrimaryKey = isPrimaryKey,
            ),
            autoDetectedCursor = autoDetected,
        )
    }

    private fun resolveDriftStatus(
        storedTable: CustomSourceTableMappingEntity?,
        storedFields: List<CustomSourceFieldMappingEntity>,
        liveTable: TableSchema,
        freshHash: String,
    ): DriftStatus {
        if (storedTable == null) return DriftStatus.NEW
        if (storedTable.schemaHash != freshHash) return DriftStatus.DRIFTED

        // Belt-and-suspenders: schema-hash collision would also be caught by
        // a column-set comparison — treat any added column as DRIFTED.
        val liveNames = liveTable.columns.map { it.name }.toSet()
        val storedNames = storedFields.map { it.columnName }.toSet()
        val added = liveNames - storedNames
        val dropped = storedNames - liveNames
        return if (added.isNotEmpty() || dropped.isNotEmpty()) DriftStatus.DRIFTED else DriftStatus.CLEAN
    }

    private fun markDroppedColumnsStale(
        storedFields: List<CustomSourceFieldMappingEntity>,
        liveColumns: List<ColumnSchema>,
    ) {
        val liveNames = liveColumns.map { it.name }.toSet()
        storedFields
            .filter { it.columnName !in liveNames && !it.stale }
            .forEach { droppedField ->
                droppedField.stale = true
                fieldMappingRepository.save(droppedField)
            }
    }

    private fun buildCursorIndexWarning(
        connectionId: UUID,
        credentials: CredentialPayload,
        schema: String,
        tableName: String,
        columnName: String,
    ): CursorIndexWarning? {
        val indexed = cursorIndexProbe.isIndexed(connectionId, credentials, schema, tableName, columnName)
        if (indexed) return null
        val ddl = "CREATE INDEX ON \"$schema\".\"$tableName\" (\"$columnName\");"
        return CursorIndexWarning(column = columnName, suggestedDdl = ddl)
    }

    /**
     * Heuristic: the first column whose name is `updated_at` / `modified_at` /
     * `last_modified` (case-insensitive) and whose pg type is in the timestamp
     * family. Returns null when no candidate matches.
     */
    private fun autoDetectCursorColumn(columns: List<ColumnSchema>): String? {
        val names = setOf("updated_at", "modified_at", "last_modified")
        return columns.firstOrNull { col ->
            col.name.lowercase() in names &&
                col.typeLiteral.lowercase().let { it.startsWith("timestamp") || it == "timestamptz" }
        }?.name
    }

    @Suppress("unused")
    private val unusedDomainDefault: LifecycleDomain = LifecycleDomain.UNCATEGORIZED

    @Suppress("unused")
    private val unusedGroupDefault: SemanticGroup = SemanticGroup.UNCATEGORIZED
}
