package cranium.core.service.connector.mapping

import io.github.oshai.kotlinlogging.KLogger
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.connector.DataConnectorFieldMappingEntity
import cranium.core.entity.connector.DataConnectorTableMappingEntity
import cranium.core.entity.entity.EntityTypeEntity
import cranium.core.entity.entity.RelationshipDefinitionEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.core.DataType
import cranium.core.enums.entity.EntityRelationshipCardinality
import cranium.core.enums.integration.SourceType
import cranium.core.enums.util.OperationType
import cranium.core.exceptions.connector.MappingValidationException
import cranium.core.models.common.validation.Schema
import cranium.core.models.connector.CredentialPayload
import cranium.core.models.connector.CursorIndexWarning
import cranium.core.models.connector.request.SaveDataConnectorMappingRequest
import cranium.core.models.connector.response.DataConnectorMappingSaveResponse
import cranium.core.models.connector.response.PendingRelationship
import cranium.core.models.entity.EntityTypeSchema
import cranium.core.models.ingestion.adapter.ColumnSchema
import cranium.core.repository.connector.DataConnectorConnectionRepository
import cranium.core.repository.connector.DataConnectorFieldMappingRepository
import cranium.core.repository.connector.DataConnectorTableMappingRepository
import cranium.core.repository.entity.EntityTypeRepository
import cranium.core.repository.entity.RelationshipDefinitionRepository
import cranium.core.service.activity.ActivityService
import cranium.core.service.activity.log
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.connector.CredentialEncryptionService
import cranium.core.service.connector.EncryptedCredentials
import cranium.core.service.connector.postgres.ForeignKeyMetadata
import cranium.core.service.connector.postgres.PostgresAdapter
import cranium.core.service.connector.postgres.SchemaHasher
import cranium.core.service.ingestion.adapter.PostgresCallContext
import cranium.core.util.ServiceUtil.findOrThrow
import java.time.ZonedDateTime
import java.util.UUID

/**
 * POST /api/v1/connector/connections/{id}/schema/tables/{tableName}/mapping —
 * transactionally persists per-column mappings, creates (or updates) a
 * readonly [EntityTypeEntity] with `sourceType=CONNECTOR`, materialises FK
 * relationships where both ends are published, and transitions the table
 * mapping to `published=true`.
 *
 * Save order (per plan 03-03 output spec):
 * 1. validate → 2. persist field mappings → 3. persist table mapping →
 * 4. create-or-update EntityType → 5. create FK relationships →
 * 6. mark published → 7. log activity → 8. build response.
 */
@Service
@ConditionalOnProperty(prefix = "cranium.connector", name = ["enabled"], havingValue = "true")
class DataConnectorFieldMappingService(
    private val postgresAdapter: PostgresAdapter,
    private val encryptionService: CredentialEncryptionService,
    private val cursorIndexProbe: CursorIndexProbe,
    private val connectionRepository: DataConnectorConnectionRepository,
    private val tableMappingRepository: DataConnectorTableMappingRepository,
    private val fieldMappingRepository: DataConnectorFieldMappingRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val relationshipDefinitionRepository: RelationshipDefinitionRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger,
) {

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun saveMapping(
        workspaceId: UUID,
        connectionId: UUID,
        tableName: String,
        request: SaveDataConnectorMappingRequest,
    ): DataConnectorMappingSaveResponse {
        val userId = authTokenService.getUserId()
        logger.debug {
            "saveMapping start workspaceId=$workspaceId connectionId=$connectionId table=$tableName"
        }
        val credentials = resolveCredentials(workspaceId, connectionId)

        // Re-introspect at save-time so FK metadata + column shape are fresh.
        val context = PostgresCallContext(workspaceId = workspaceId, connectionId = connectionId)
        val introspection = postgresAdapter.introspectWithFkMetadata(context)
        val liveTable = introspection.schema.tables.firstOrNull { it.name == tableName }
            ?: throw MappingValidationException("Table '$tableName' not found in live introspection")

        validateRequest(request, liveTable.columns)

        val fksForThisTable = introspection.foreignKeys.filter { it.sourceTable == tableName }
        val persistedFields = persistFieldMappings(
            workspaceId, connectionId, tableName, request, liveTable.columns, fksForThisTable,
        )

        val freshHash = SchemaHasher.compute(tableName, liveTable.columns)
        val tableMapping = persistTableMapping(
            workspaceId, connectionId, tableName, request, freshHash,
        )

        val entityTypeId = createOrUpdateEntityType(
            workspaceId, tableMapping, persistedFields, request,
        )

        val (relationshipsCreated, pendingRelationships, compositeFkSkipped) =
            createFkRelationships(workspaceId, tableName, persistedFields, fksForThisTable, entityTypeId)

        markPublished(tableMapping, entityTypeId)

        val cursorIndexWarning = resolveCursorIndexWarning(
            connectionId, credentials, context.schema, tableName, request,
        )

        activityService.log(
            activity = Activity.DATA_CONNECTOR_CONNECTION,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = entityTypeId,
            "connectionId" to connectionId,
            "tableName" to tableName,
        )

        return DataConnectorMappingSaveResponse(
            entityTypeId = entityTypeId,
            relationshipsCreated = relationshipsCreated,
            pendingRelationships = pendingRelationships,
            compositeFkSkipped = compositeFkSkipped,
            cursorIndexWarning = cursorIndexWarning,
        )
    }

    // ------ Private helpers ------

    private fun resolveCredentials(workspaceId: UUID, connectionId: UUID): CredentialPayload {
        val entity = findOrThrow { connectionRepository.findByIdAndWorkspaceId(connectionId, workspaceId) }
        val decryptedJson = encryptionService.decrypt(
            EncryptedCredentials(entity.encryptedCredentials, entity.iv, entity.keyVersion),
        )
        return objectMapper.readValue(decryptedJson)
    }

    private fun validateRequest(
        request: SaveDataConnectorMappingRequest,
        liveColumns: List<ColumnSchema>,
    ) {
        val liveNames = liveColumns.map { it.name }.toSet()
        val unknown = request.columns.map { it.columnName }.filter { it !in liveNames }
        if (unknown.isNotEmpty()) {
            throw MappingValidationException(
                "Request references unknown columns: ${unknown.joinToString(", ")}",
            )
        }

        val identifierCount = request.columns.count { it.isIdentifier }
        if (identifierCount > 1) {
            throw MappingValidationException("At most one column may be flagged as identifier; got $identifierCount")
        }

        val cursorCount = request.columns.count { it.isSyncCursor }
        if (cursorCount > 1) {
            throw MappingValidationException("At most one column may be flagged as syncCursor; got $cursorCount")
        }

        val missingAttrName = request.columns.filter { it.isMapped && it.attributeName.isNullOrBlank() }
        if (missingAttrName.isNotEmpty()) {
            throw MappingValidationException(
                "Mapped columns require attributeName: ${missingAttrName.joinToString(", ") { it.columnName }}",
            )
        }
    }

    private fun persistFieldMappings(
        workspaceId: UUID,
        connectionId: UUID,
        tableName: String,
        request: SaveDataConnectorMappingRequest,
        liveColumns: List<ColumnSchema>,
        fks: List<ForeignKeyMetadata>,
    ): List<DataConnectorFieldMappingEntity> {
        val stored = fieldMappingRepository
            .findByConnectionIdAndTableName(connectionId, tableName)
            .associateBy { it.columnName }
        val liveByName = liveColumns.associateBy { it.name }

        // Mark stored columns absent from the live shape as stale.
        stored.values
            .filter { it.columnName !in liveByName && !it.stale }
            .forEach {
                it.stale = true
                fieldMappingRepository.save(it)
            }

        val requestByColumn = request.columns.associateBy { it.columnName }

        // Upsert a row per live column (even ones the user did NOT map — so we
        // retain pg_data_type + PK/FK metadata for future re-Save).
        return liveColumns.map { live ->
            val existing = stored[live.name]
            val req = requestByColumn[live.name]
            val fk = fks.firstOrNull { it.sourceColumn == live.name && !it.isComposite }
            val attributeName = req?.attributeName?.takeIf { it.isNotBlank() }
                ?: existing?.attributeName
                ?: live.name
            val schemaType: SchemaType = req?.schemaType ?: existing?.schemaType ?: SchemaType.TEXT

            val entity = existing ?: DataConnectorFieldMappingEntity(
                workspaceId = workspaceId,
                connectionId = connectionId,
                tableName = tableName,
                columnName = live.name,
                pgDataType = live.typeLiteral,
                nullable = live.nullable,
                attributeName = attributeName,
                schemaType = schemaType,
            )
            entity.pgDataType = live.typeLiteral
            entity.nullable = live.nullable
            entity.isPrimaryKey = existing?.isPrimaryKey ?: false
            entity.isForeignKey = fk != null
            entity.fkTargetTable = fk?.targetTable
            entity.fkTargetColumn = fk?.targetColumn
            entity.attributeName = attributeName
            entity.schemaType = schemaType
            entity.isIdentifier = req?.isIdentifier ?: false
            entity.isSyncCursor = req?.isSyncCursor ?: false
            entity.isMapped = req?.isMapped ?: false
            entity.stale = false

            fieldMappingRepository.save(entity)
        }
    }

    private fun persistTableMapping(
        workspaceId: UUID,
        connectionId: UUID,
        tableName: String,
        request: SaveDataConnectorMappingRequest,
        freshHash: String,
    ): DataConnectorTableMappingEntity {
        val existing = tableMappingRepository.findByConnectionIdAndTableName(connectionId, tableName)
        val entity = existing ?: DataConnectorTableMappingEntity(
            workspaceId = workspaceId,
            connectionId = connectionId,
            tableName = tableName,
            lifecycleDomain = request.lifecycleDomain,
            semanticGroup = request.semanticGroup,
            schemaHash = freshHash,
            lastIntrospectedAt = ZonedDateTime.now(),
            published = false,
        )
        entity.lifecycleDomain = request.lifecycleDomain
        entity.semanticGroup = request.semanticGroup
        entity.schemaHash = freshHash
        entity.lastIntrospectedAt = ZonedDateTime.now()
        return tableMappingRepository.save(entity)
    }

    private fun createOrUpdateEntityType(
        workspaceId: UUID,
        tableMapping: DataConnectorTableMappingEntity,
        fieldMappings: List<DataConnectorFieldMappingEntity>,
        request: SaveDataConnectorMappingRequest,
    ): UUID {
        val mappedFields = fieldMappings.filter { it.isMapped && !it.stale }
        val identifierColumn = mappedFields.firstOrNull { it.isIdentifier }
            ?: fieldMappings.firstOrNull { it.isPrimaryKey }
            ?: throw MappingValidationException(
                "Cannot derive identifierKey for table '${tableMapping.tableName}': " +
                    "no column flagged as identifier and no primary-key column present. " +
                    "Flag a mapped column as identifier, or ensure the source table has a primary key.",
            )
        val identifierKey = requireNotNull(identifierColumn.id) { "field mapping id must be set after save" }

        val properties: Map<UUID, Schema<UUID>> = mappedFields.associate { field ->
            val attrId = requireNotNull(field.id) { "field mapping id must be set after save" }
            attrId to Schema<UUID>(
                label = field.attributeName,
                key = field.schemaType,
                type = dataTypeFor(field.schemaType),
                required = !field.nullable,
            )
        }

        val schema: EntityTypeSchema = Schema(
            type = DataType.OBJECT,
            key = SchemaType.OBJECT,
            protected = true,
            required = true,
            properties = properties,
        )

        val existingId = tableMapping.entityTypeId
        val entity = if (existingId != null) {
            val existing = findOrThrow { entityTypeRepository.findById(existingId) }
            existing.schema = schema
            existing.semanticGroup = request.semanticGroup
            existing.lifecycleDomain = request.lifecycleDomain
            existing.version += 1
            entityTypeRepository.save(existing)
        } else {
            val fresh = EntityTypeEntity(
                key = "connector_${tableMapping.connectionId}_${tableMapping.tableName}".take(255),
                displayNameSingular = tableMapping.tableName,
                displayNamePlural = tableMapping.tableName,
                semanticGroup = request.semanticGroup,
                lifecycleDomain = request.lifecycleDomain,
                sourceType = SourceType.CONNECTOR,
                readonly = true,
                identifierKey = identifierKey,
                workspaceId = workspaceId,
                schema = schema,
            )
            entityTypeRepository.save(fresh)
        }
        return requireNotNull(entity.id) { "EntityTypeEntity.id must not be null after save" }
    }

    private data class FkMaterialisation(
        val created: List<UUID>,
        val pending: List<PendingRelationship>,
        val composite: List<String>,
    )

    private fun createFkRelationships(
        workspaceId: UUID,
        tableName: String,
        fieldMappings: List<DataConnectorFieldMappingEntity>,
        fks: List<ForeignKeyMetadata>,
        sourceEntityTypeId: UUID,
    ): FkMaterialisation {
        val created = mutableListOf<UUID>()
        val pending = mutableListOf<PendingRelationship>()
        val composite = mutableListOf<String>()

        fks.forEach { fk ->
            if (fk.isComposite) {
                composite.add("${fk.sourceTable}.${fk.sourceColumn} -> ${fk.targetTable}.${fk.targetColumn}")
                return@forEach
            }

            val targetTableMapping = tableMappingRepository
                .findByConnectionIdAndTableName(
                    fieldMappings.first().connectionId,
                    fk.targetTable,
                )

            if (targetTableMapping == null || !targetTableMapping.published || targetTableMapping.entityTypeId == null) {
                pending.add(PendingRelationship(targetTable = fk.targetTable, targetColumn = fk.targetColumn))
                return@forEach
            }

            val fkField = fieldMappings.firstOrNull { it.columnName == fk.sourceColumn }
            val cardinality = if (fkField?.nullable == true) {
                EntityRelationshipCardinality.ONE_TO_MANY
            } else {
                EntityRelationshipCardinality.MANY_TO_ONE
            }

            val rel = RelationshipDefinitionEntity(
                workspaceId = workspaceId,
                sourceEntityTypeId = sourceEntityTypeId,
                name = "${tableName}_${fk.sourceColumn}_fk",
                cardinalityDefault = cardinality,
            )
            val saved = relationshipDefinitionRepository.save(rel)
            created.add(requireNotNull(saved.id) { "RelationshipDefinitionEntity.id must be non-null after save" })
        }

        return FkMaterialisation(created, pending, composite)
    }

    private fun markPublished(tableMapping: DataConnectorTableMappingEntity, entityTypeId: UUID) {
        tableMapping.published = true
        tableMapping.entityTypeId = entityTypeId
        tableMappingRepository.save(tableMapping)
    }

    private fun resolveCursorIndexWarning(
        connectionId: UUID,
        credentials: CredentialPayload,
        schema: String,
        tableName: String,
        request: SaveDataConnectorMappingRequest,
    ): CursorIndexWarning? {
        val cursor = request.columns.firstOrNull { it.isSyncCursor }?.columnName ?: return null
        val indexed = cursorIndexProbe.isIndexed(connectionId, credentials, schema, tableName, cursor)
        if (indexed) return null
        val ddl = "CREATE INDEX ON \"$schema\".\"$tableName\" (\"$cursor\");"
        return CursorIndexWarning(column = cursor, suggestedDdl = ddl)
    }

    private fun dataTypeFor(schemaType: SchemaType): DataType = when (schemaType) {
        SchemaType.NUMBER, SchemaType.RATING, SchemaType.CURRENCY, SchemaType.PERCENTAGE -> DataType.NUMBER
        SchemaType.CHECKBOX -> DataType.BOOLEAN
        SchemaType.OBJECT, SchemaType.LOCATION -> DataType.OBJECT
        SchemaType.MULTI_SELECT -> DataType.ARRAY
        else -> DataType.STRING
    }
}
