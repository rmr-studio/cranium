package cranium.core.service.connector.mapping

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.connector.DataConnectorFieldMappingEntity
import cranium.core.entity.connector.DataConnectorTableMappingEntity
import cranium.core.enums.entity.LifecycleDomain
import cranium.core.enums.entity.semantics.SemanticGroup
import cranium.core.models.connector.CredentialPayload
import cranium.core.models.connector.CursorIndexWarning
import cranium.core.models.connector.response.ColumnSchemaResponse
import cranium.core.models.connector.response.DataConnectorSchemaResponse
import cranium.core.models.connector.response.DriftStatus
import cranium.core.models.connector.response.ExistingMappingRef
import cranium.core.models.connector.response.FkTargetRef
import cranium.core.models.connector.response.TableSchemaResponse
import cranium.core.models.ingestion.adapter.ColumnSchema
import cranium.core.models.ingestion.adapter.TableSchema
import cranium.core.repository.connector.DataConnectorConnectionRepository
import cranium.core.repository.connector.DataConnectorFieldMappingRepository
import cranium.core.repository.connector.DataConnectorTableMappingRepository
import cranium.core.service.connector.CredentialEncryptionService
import cranium.core.service.connector.EncryptedCredentials
import cranium.core.service.connector.postgres.ForeignKeyMetadata
import cranium.core.service.connector.postgres.PgTypeMapper
import cranium.core.service.connector.postgres.PostgresAdapter
import cranium.core.service.connector.postgres.SchemaHasher
import cranium.core.service.ingestion.adapter.PostgresCallContext
import cranium.core.util.ServiceUtil.findOrThrow
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
@ConditionalOnProperty(prefix = "cranium.connector", name = ["enabled"], havingValue = "true")
class DataConnectorSchemaInferenceService(
    private val postgresAdapter: PostgresAdapter,
    private val encryptionService: CredentialEncryptionService,
    private val connectionRepository: DataConnectorConnectionRepository,
    private val tableMappingRepository: DataConnectorTableMappingRepository,
    private val fieldMappingRepository: DataConnectorFieldMappingRepository,
    private val cursorIndexProbe: CursorIndexProbe,
    private val objectMapper: ObjectMapper,
    @Suppress("unused") private val logger: KLogger,
) {

    /**
     * Introspect the live source schema, merge with stored mappings, and return
     * per-table drift + FK + cursor-index warnings. Marks stale (a) columns no
     * longer present in the live table and (b) field mappings whose parent
     * table has been dropped upstream. Dropped tables are surfaced via
     * [DataConnectorSchemaResponse.staleDroppedTables]. Called by the schema
     * inference endpoint prior to mapping Save.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getSchema(workspaceId: UUID, connectionId: UUID): DataConnectorSchemaResponse {
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

        val liveTableNames = introspection.schema.tables.map { it.name }.toSet()
        val droppedTables = (storedTables.keys - liveTableNames).toList().sorted()
        markDroppedTableFieldsStale(droppedTables, storedFields)

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

        return DataConnectorSchemaResponse(
            tables = tableResponses,
            staleDroppedTables = droppedTables,
        )
    }

    private fun markDroppedTableFieldsStale(
        droppedTables: List<String>,
        storedFields: Map<String, List<DataConnectorFieldMappingEntity>>,
    ) {
        droppedTables.forEach { tableName ->
            storedFields[tableName].orEmpty()
                .filter { !it.stale }
                .forEach { field ->
                    field.stale = true
                    fieldMappingRepository.save(field)
                }
        }
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
        storedTable: DataConnectorTableMappingEntity?,
        storedFields: List<DataConnectorFieldMappingEntity>,
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
        stored: DataConnectorFieldMappingEntity?,
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
        storedTable: DataConnectorTableMappingEntity?,
        storedFields: List<DataConnectorFieldMappingEntity>,
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
        storedFields: List<DataConnectorFieldMappingEntity>,
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
