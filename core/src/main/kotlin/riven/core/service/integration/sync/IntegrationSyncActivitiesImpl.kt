package riven.core.service.integration.sync

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.activity.Activity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import riven.core.entity.catalog.CatalogEntityTypeEntity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityRelationshipEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.integration.IntegrationSyncStateEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.integration.CoercionType
import riven.core.enums.integration.ConnectionStatus
import riven.core.enums.integration.SourceType
import riven.core.enums.integration.SyncStatus
import riven.core.models.integration.NangoRecord
import riven.core.models.integration.NangoRecordAction
import riven.core.models.integration.sync.IntegrationSyncWorkflowInput
import riven.core.models.integration.sync.RelationshipPending
import riven.core.models.integration.sync.SyncProcessingResult
import riven.core.models.integration.mapping.FieldTransform
import riven.core.models.integration.mapping.ResolvedFieldMapping
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.catalog.CatalogFieldMappingRepository
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.integration.IntegrationConnectionRepository
import riven.core.repository.integration.IntegrationDefinitionRepository
import riven.core.repository.integration.IntegrationSyncStateRepository
import riven.core.service.entity.EntityAttributeService
import riven.core.service.integration.IntegrationHealthService
import riven.core.service.integration.NangoClientWrapper
import riven.core.service.integration.mapping.SchemaMappingService
import java.time.ZonedDateTime
import java.util.*

/**
 * Spring service implementing all four Temporal activity methods for the integration sync workflow.
 *
 * This is the core data pipeline. Records flow from Nango into workspace entities with:
 * - Deduplication via batch IN-clause query (SYNC-02)
 * - Idempotent upsert semantics for ADDED/UPDATED/DELETED actions (SYNC-03, SYNC-04, SYNC-05)
 * - Per-record error isolation — single failures do not abort the batch (SYNC-06)
 * - Paginated fetch with heartbeating to prevent activity timeout (SYNC-01)
 * - Two-pass processing: upsert first, then resolve relationships (SYNC-07)
 * - Connection health evaluation after each sync cycle (HLTH-01, HLTH-02, HLTH-03)
 *
 * @see IntegrationSyncActivities for the activity interface
 * @see IntegrationSyncWorkflowImpl for the workflow that orchestrates these activities
 */
@Service
class IntegrationSyncActivitiesImpl(
    private val connectionRepository: IntegrationConnectionRepository,
    private val syncStateRepository: IntegrationSyncStateRepository,
    private val nangoClientWrapper: NangoClientWrapper,
    private val schemaMappingService: SchemaMappingService,
    private val entityRepository: EntityRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val relationshipDefinitionRepository: RelationshipDefinitionRepository,
    private val definitionRepository: IntegrationDefinitionRepository,
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val catalogFieldMappingRepository: CatalogFieldMappingRepository,
    private val catalogEntityTypeRepository: CatalogEntityTypeRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val integrationHealthService: IntegrationHealthService,
    private val entityProjectionService: riven.core.service.ingestion.EntityProjectionService,
    private val transactionTemplate: TransactionTemplate,
    private val logger: KLogger,
) : IntegrationSyncActivities {

    // ------ Activity Methods ------

    /**
     * Transitions the integration connection to SYNCING status.
     *
     * Non-transitionable states are logged and skipped (do not throw) so the workflow
     * can proceed. Missing connections are treated as a no-op.
     */
    override fun transitionToSyncing(connectionId: UUID, workspaceId: UUID) {
        val connection = connectionRepository.findById(connectionId).orElse(null)
        if (connection == null) {
            logger.warn { "Connection $connectionId not found, skipping transitionToSyncing" }
            return
        }

        if (connection.workspaceId != workspaceId) {
            logger.error { "Connection $connectionId belongs to workspace ${connection.workspaceId}, not $workspaceId — skipping transitionToSyncing" }
            return
        }

        if (connection.status == ConnectionStatus.SYNCING) {
            logger.info { "Connection $connectionId already SYNCING, skipping transition" }
            return
        }

        if (!connection.status.canTransitionTo(ConnectionStatus.SYNCING)) {
            logger.warn { "Connection $connectionId in state ${connection.status} cannot transition to SYNCING, skipping" }
            return
        }

        connection.status = ConnectionStatus.SYNCING
        connectionRepository.save(connection)
        logger.info { "Transitioned connection $connectionId to SYNCING" }
    }

    /**
     * Fetches all records from Nango for the given model and upserts them as workspace entities.
     *
     * Resolves model context, paginates through all records with heartbeating, processes each
     * batch with deduplication, and runs a second pass to resolve pending relationships.
     */
    override fun fetchAndProcessRecords(input: IntegrationSyncWorkflowInput): SyncProcessingResult {
        val context = resolveModelContext(input) ?: return buildFailureResult(
            entityTypeId = null,
            message = "Failed to resolve model context for model ${input.model}",
        )

        val resolvedCursor = resolveStartCursor(input, context.entityTypeId)

        return runFetchAndProcessLoop(input, context, resolvedCursor)
    }

    /**
     * Finalizes the sync state after processing completes.
     *
     * Lazy-creates the IntegrationSyncStateEntity if not found. On success, advances the cursor
     * and resets the failure count. On failure, preserves the last cursor and increments the count.
     */
    @Transactional
    override fun finalizeSyncState(connectionId: UUID, entityTypeId: UUID, result: SyncProcessingResult) {
        val existing = syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(connectionId, entityTypeId)
        val state = existing ?: IntegrationSyncStateEntity(
            integrationConnectionId = connectionId,
            entityTypeId = entityTypeId,
        )

        state.status = if (result.success) SyncStatus.SUCCESS else SyncStatus.FAILED
        state.lastRecordsSynced = result.recordsSynced
        state.lastRecordsFailed = result.recordsFailed
        state.lastErrorMessage = result.lastErrorMessage

        if (result.success) {
            state.lastCursor = result.cursor
            state.consecutiveFailureCount = 0
        } else {
            state.consecutiveFailureCount = (existing?.consecutiveFailureCount ?: 0) + 1
        }

        syncStateRepository.save(state)
        logger.info { "Finalized sync state for connection=$connectionId entityType=$entityTypeId success=${result.success}" }
    }

    /**
     * Executes the projection pipeline (Pass 3): projects integration entities into core lifecycle entities.
     *
     * Delegates to [EntityProjectionService] which handles rule loading, identity resolution,
     * entity creation/update, relationship linking, and cluster assignment.
     */
    override fun executeProjections(connectionId: UUID, workspaceId: UUID, entityTypeId: UUID, syncedEntityIds: List<UUID>) {
        if (syncedEntityIds.isEmpty()) {
            logger.info { "Pass 3 (projections): No entities to project for connection=$connectionId" }
            return
        }

        logger.info { "Pass 3 (projections): Projecting ${syncedEntityIds.size} entities for connection=$connectionId, entityType=$entityTypeId" }

        val result = entityProjectionService.processProjections(
            syncedEntityIds = syncedEntityIds,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
        )

        logger.info {
            "Pass 3 (projections): Complete for connection=$connectionId — " +
                "created=${result.created}, updated=${result.updated}, skipped=${result.skipped}, errors=${result.errors}"
        }
    }

    /**
     * Evaluates connection health by delegating to [IntegrationHealthService].
     *
     * Runs in its own transaction boundary (separate from [finalizeSyncState]), so a failure
     * here does not roll back the sync state write. All business logic lives in the service.
     */
    override fun evaluateHealth(connectionId: UUID) {
        integrationHealthService.evaluateConnectionHealth(connectionId)
    }

    // ------ Model Context Resolution ------

    /**
     * Resolves all catalog and entity type context needed to process a model's records.
     *
     * Returns null if any lookup fails — the caller treats this as a fatal activity result.
     */
    private fun resolveModelContext(input: IntegrationSyncWorkflowInput): ModelContext? {
        val definition = definitionRepository.findById(input.integrationId).orElse(null)
        if (definition == null) {
            logger.error { "IntegrationDefinition not found for id=${input.integrationId}" }
            return null
        }

        val manifest = manifestCatalogRepository.findByKey(definition.slug).firstOrNull()
        if (manifest == null) {
            logger.error { "ManifestCatalog not found for key=${definition.slug}" }
            return null
        }

        val manifestId = requireNotNull(manifest.id) { "ManifestCatalogEntity.id must not be null" }

        // Resolve Nango model name -> entity type key via nangoModel on field mapping
        val fieldMappingEntity = catalogFieldMappingRepository.findByManifestIdAndNangoModel(manifestId, input.model)
        if (fieldMappingEntity == null) {
            logger.error { "CatalogFieldMapping not found for manifestId=$manifestId nangoModel=${input.model}" }
            return null
        }
        val entityTypeKey = fieldMappingEntity.entityTypeKey

        val entityType = entityTypeRepository
            .findBySourceIntegrationIdAndWorkspaceId(input.integrationId, input.workspaceId)
            .firstOrNull { it.key == entityTypeKey }
        if (entityType == null) {
            logger.error { "EntityType not found for integrationId=${input.integrationId} workspaceId=${input.workspaceId} entityTypeKey=$entityTypeKey" }
            return null
        }

        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity.id must not be null" }

        val catalogEntityType = catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityTypeKey)
        if (catalogEntityType == null) {
            logger.error { "CatalogEntityType not found for manifestId=$manifestId entityTypeKey=$entityTypeKey" }
            return null
        }

        val keyMapping = buildKeyMapping(catalogEntityType, entityType)
        val fieldMappings = resolveFieldMappings(fieldMappingEntity.mappings, keyMapping, entityType)
        val externalIdField = resolveExternalIdField(fieldMappingEntity.mappings)

        return ModelContext(
            entityTypeId = entityTypeId,
            typeKey = entityType.key,
            identifierKey = entityType.identifierKey,
            fieldMappings = fieldMappings,
            keyMapping = keyMapping,
            externalIdField = externalIdField,
        )
    }

    /**
     * Builds the attribute string-key-to-UUID mapping by zipping the ordered catalog schema
     * string keys with the installed entity type's column order (UUIDs).
     *
     * Both lists are in the same insertion order from the original manifest, so zipping
     * them produces the correct string-key-to-UUID map.
     */
    private fun buildKeyMapping(
        catalogEntityType: CatalogEntityTypeEntity,
        entityType: EntityTypeEntity,
    ): Map<String, UUID> {
        val stringKeys = catalogEntityType.schema.keys.toList()
        val uuidKeys = entityType.columnConfiguration?.order ?: emptyList()
        return stringKeys.zip(uuidKeys).associate { (stringKey, uuid) -> stringKey to uuid }
    }

    /**
     * Converts the raw JSONB field mappings (Map<String, Any>) into ResolvedFieldMapping objects
     * usable by SchemaMappingService.
     *
     * Each entry in the mappings map is an attribute key → { source: String, transform: {...} } structure.
     * Special reserved keys starting with "_" are skipped. The SchemaType is looked up from the
     * installed entity type's schema properties to ensure correct attribute creation.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveFieldMappings(
        rawMappings: Map<String, Any>,
        keyMapping: Map<String, UUID>,
        entityType: EntityTypeEntity,
    ): Map<String, ResolvedFieldMapping> {
        val result = mutableMapOf<String, ResolvedFieldMapping>()

        for ((attrKey, rawDef) in rawMappings) {
            if (attrKey.startsWith("_")) continue // Reserved keys (e.g. _externalIdField)

            val attrUuid = keyMapping[attrKey] ?: continue
            val def = rawDef as? Map<String, Any> ?: continue
            val sourcePath = def["source"] as? String ?: continue

            val schemaType = entityType.schema.properties?.get(attrUuid)?.key ?: SchemaType.TEXT

            result[attrKey] = ResolvedFieldMapping(
                sourcePath = sourcePath,
                transform = parseTransform(def),
                targetSchemaType = schemaType,
            )
        }

        return result
    }

    /**
     * Parses a transform block from the raw JSONB field mapping into a FieldTransform.
     *
     * Handles all four transform types: direct, type_coercion, default_value, json_path_extraction.
     * Returns FieldTransform.Direct if the transform block is missing or unrecognised.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseTransform(rawDef: Map<String, Any>): FieldTransform {
        val transformMap = rawDef["transform"] as? Map<String, Any> ?: return FieldTransform.Direct
        val type = transformMap["type"] as? String ?: return FieldTransform.Direct

        return when (type) {
            "direct" -> FieldTransform.Direct
            "type_coercion" -> {
                val targetType = transformMap["targetType"] as? String
                    ?: return FieldTransform.Direct
                val coercionType = CoercionType.entries.firstOrNull { it.name.equals(targetType, ignoreCase = true) }
                if (coercionType == null) {
                    logger.warn { "Unknown coercion targetType '$targetType' — falling back to Direct" }
                    return FieldTransform.Direct
                }
                FieldTransform.TypeCoercion(coercionType)
            }
            "default_value" -> {
                val value = transformMap["value"]
                FieldTransform.DefaultValue(value)
            }
            "json_path_extraction" -> {
                val path = transformMap["path"] as? String
                    ?: return FieldTransform.Direct
                FieldTransform.JsonPathExtraction(path)
            }
            else -> {
                logger.warn { "Unknown transform type '$type' — falling back to Direct" }
                FieldTransform.Direct
            }
        }
    }

    /**
     * Resolves the external ID field name from the field mapping JSONB.
     *
     * Checks for a reserved `_externalIdField` key in the mappings. Falls back to `"id"`,
     * which is the standard Nango field name for a record's primary key.
     */
    private fun resolveExternalIdField(rawMappings: Map<String, Any>): String {
        return (rawMappings["_externalIdField"] as? String) ?: "id"
    }

    // ------ Cursor Resolution ------

    /**
     * Resolves the starting cursor for this sync run.
     *
     * Checks the persisted sync state first (source of truth for incremental sync),
     * then falls back to the workflow input's modifiedAfter value.
     */
    private fun resolveStartCursor(input: IntegrationSyncWorkflowInput, entityTypeId: UUID): String? {
        val existingState = syncStateRepository.findByIntegrationConnectionIdAndEntityTypeId(
            input.connectionId, entityTypeId
        )
        return existingState?.lastCursor ?: input.modifiedAfter
    }

    // ------ Main Fetch/Process Loop ------

    /**
     * Runs the paginated fetch and process loop until all pages are consumed.
     *
     * Sends a heartbeat after each page to prevent Temporal activity timeout.
     * Collects pending relationships across all pages and resolves them in Pass 2.
     */
    private fun runFetchAndProcessLoop(
        input: IntegrationSyncWorkflowInput,
        context: ModelContext,
        startCursor: String?,
    ): SyncProcessingResult {
        var cursor = startCursor
        var lastSuccessfulCursor: String? = startCursor
        val relationshipPending = mutableListOf<RelationshipPending>()
        var recordsSynced = 0
        var recordsFailed = 0
        var lastErrorMessage: String? = null
        val allSyncedEntityIds = mutableListOf<UUID>()

        do {
            val page = nangoClientWrapper.fetchRecords(
                providerConfigKey = input.providerConfigKey,
                connectionId = input.nangoConnectionId,
                model = input.model,
                cursor = cursor,
                modifiedAfter = input.modifiedAfter,
                limit = null,
            )

            val batchResult = processBatch(
                records = page.records,
                workspaceId = input.workspaceId,
                integrationId = input.integrationId,
                context = context,
                relationshipPending = relationshipPending,
            )

            recordsSynced += batchResult.synced
            recordsFailed += batchResult.failed
            if (batchResult.lastError != null) lastErrorMessage = batchResult.lastError
            allSyncedEntityIds.addAll(batchResult.syncedEntityIds)

            lastSuccessfulCursor = cursor
            cursor = page.nextCursor
            heartbeat(cursor)
        } while (cursor != null)

        resolveRelationships(relationshipPending, input.workspaceId, input.integrationId)

        val success = recordsSynced > 0 || recordsFailed == 0

        return SyncProcessingResult(
            entityTypeId = context.entityTypeId,
            cursor = lastSuccessfulCursor,
            recordsSynced = recordsSynced,
            recordsFailed = recordsFailed,
            lastErrorMessage = lastErrorMessage,
            success = success,
            syncedEntityIds = allSyncedEntityIds,
        )
    }

    /**
     * Sends a heartbeat to Temporal with the current cursor as progress data.
     *
     * Heartbeating prevents the activity from timing out during long pagination and allows
     * resumption from the last cursor on retry. Silently swallowed in test contexts where
     * Temporal's static activity execution context is unavailable.
     */
    internal open fun heartbeat(cursor: String?) {
        try {
            Activity.getExecutionContext().heartbeat(cursor)
        } catch (_: IllegalStateException) {
            // In test environments, Temporal's static activity execution context is not available.
        }
    }

    // ------ Batch Processing ------

    /**
     * Processes a single page of records with deduplication, error isolation, and upsert logic.
     *
     * Uses a batch IN-clause query for dedup (O(1) per-record access via Map). Each record is
     * wrapped in try-catch so a single failure does not abort the rest of the batch.
     */
    private fun processBatch(
        records: List<NangoRecord>,
        workspaceId: UUID,
        integrationId: UUID,
        context: ModelContext,
        relationshipPending: MutableList<RelationshipPending>,
    ): BatchResult {
        if (records.isEmpty()) return BatchResult(0, 0, null)

        val externalIds = records.mapNotNull { it.payload[context.externalIdField] as? String }
        val existingByExternalId = if (externalIds.isNotEmpty()) {
            entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                workspaceId, integrationId, externalIds
            ).associateBy {
                requireNotNull(it.sourceExternalId) { "EntityEntity.sourceExternalId must not be null for INTEGRATION-sourced entity" }
            }
        } else {
            emptyMap()
        }

        var synced = 0
        var failed = 0
        var lastError: String? = null
        val syncedEntityIds = mutableListOf<UUID>()

        for (record in records) {
            try {
                val externalId = record.payload[context.externalIdField] as? String
                if (externalId == null) {
                    logger.error { "Record missing externalId field '${context.externalIdField}' — skipping" }
                    failed++
                    lastError = "Missing externalId field '${context.externalIdField}'"
                    continue
                }

                val existing = existingByExternalId[externalId]
                val isDelete = record.nangoMetadata.lastAction == NangoRecordAction.DELETED
                val upsertedEntityId = transactionTemplate.execute {
                    processRecord(
                        record = record,
                        externalId = externalId,
                        existing = existing,
                        workspaceId = workspaceId,
                        integrationId = integrationId,
                        context = context,
                        relationshipPending = relationshipPending,
                    )
                }

                if (upsertedEntityId != null) {
                    synced++
                    syncedEntityIds.add(upsertedEntityId)
                } else if (isDelete) {
                    synced++ // Deletes are successful processing but produce no entity for projection
                } else {
                    failed++
                }
            } catch (e: Exception) {
                val externalId = record.payload[context.externalIdField] as? String ?: "unknown"
                logger.error(e) { "Error processing record externalId=$externalId — skipping" }
                failed++
                lastError = e.message
            }
        }

        return BatchResult(synced, failed, lastError, syncedEntityIds)
    }

    /**
     * Processes a single record based on its action: ADDED, UPDATED, or DELETED.
     *
     * Returns the entity UUID on successful upsert, null on delete or failure.
     */
    private fun processRecord(
        record: NangoRecord,
        externalId: String,
        existing: EntityEntity?,
        workspaceId: UUID,
        integrationId: UUID,
        context: ModelContext,
        relationshipPending: MutableList<RelationshipPending>,
    ): UUID? {
        return when (record.nangoMetadata.lastAction) {
            NangoRecordAction.DELETED -> {
                handleDelete(existing)
                null
            }
            NangoRecordAction.ADDED, NangoRecordAction.UPDATED -> {
                handleUpsert(
                    record = record,
                    externalId = externalId,
                    existing = existing,
                    workspaceId = workspaceId,
                    integrationId = integrationId,
                    context = context,
                    relationshipPending = relationshipPending,
                )
            }
        }
    }

    /**
     * Soft-deletes an existing entity. If the entity doesn't exist, this is a no-op.
     */
    private fun handleDelete(existing: EntityEntity?) {
        if (existing == null) return
        existing.deleted = true
        existing.deletedAt = ZonedDateTime.now()
        entityRepository.save(existing)
    }

    /**
     * Upserts a record: maps payload to attributes, then creates or updates the entity.
     *
     * Returns the entity UUID if successful, null if mapping errors prevent the record from being processed.
     */
    private fun handleUpsert(
        record: NangoRecord,
        externalId: String,
        existing: EntityEntity?,
        workspaceId: UUID,
        integrationId: UUID,
        context: ModelContext,
        relationshipPending: MutableList<RelationshipPending>,
    ): UUID? {
        val mappingResult = schemaMappingService.mapPayload(
            externalPayload = record.payload,
            fieldMappings = context.fieldMappings,
            keyMapping = context.keyMapping,
        )

        if (mappingResult.errors.isNotEmpty()) {
            logger.warn { "Mapping errors for record $externalId: ${mappingResult.errors.map { it.message }}" }
            return null
        }

        if (mappingResult.warnings.isNotEmpty()) {
            logger.debug { "Mapping warnings for record $externalId: ${mappingResult.warnings.map { it.message }}" }
        }

        val now = ZonedDateTime.now()
        val entity = if (existing != null) {
            existing.lastSyncedAt = now
            entityRepository.save(existing)
        } else {
            entityRepository.save(
                EntityEntity(
                    workspaceId = workspaceId,
                    typeId = context.entityTypeId,
                    typeKey = context.typeKey,
                    identifierKey = context.identifierKey,
                    sourceType = SourceType.INTEGRATION,
                    sourceIntegrationId = integrationId,
                    sourceExternalId = externalId,
                    firstSyncedAt = now,
                    lastSyncedAt = now,
                )
            )
        }

        val entityId = requireNotNull(entity.id) { "EntityEntity.id must not be null after save" }

        val uuidKeyedAttributes = mappingResult.attributes.mapKeys { (key, _) -> UUID.fromString(key) }
        entityAttributeService.saveAttributes(entityId, workspaceId, context.entityTypeId, uuidKeyedAttributes)

        collectRelationshipPending(record.payload, entityId, context.entityTypeId, workspaceId, relationshipPending)

        return entityId
    }

    /**
     * Collects relationship data from the record payload for Pass 2 resolution.
     *
     * Checks payload fields against known relationship definitions for this entity type.
     * Fields whose values are lists of strings and whose keys match a definition name
     * are collected as pending relationships.
     *
     * Best-effort: missing definitions or malformed values are skipped silently.
     */
    private fun collectRelationshipPending(
        payload: Map<String, Any?>,
        entityId: UUID,
        entityTypeId: UUID,
        workspaceId: UUID,
        relationshipPending: MutableList<RelationshipPending>,
    ) {
        val definitions = relationshipDefinitionRepository
            .findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)

        for (definition in definitions) {
            val definitionKey = definition.name
            @Suppress("UNCHECKED_CAST")
            val targetIds = payload[definitionKey] as? List<String> ?: continue
            if (targetIds.isEmpty()) continue

            relationshipPending.add(
                RelationshipPending(
                    sourceEntityId = entityId,
                    relationshipDefinitionKey = definitionKey,
                    targetExternalIds = targetIds,
                )
            )
        }
    }

    // ------ Pass 2: Relationship Resolution ------

    /**
     * Resolves pending relationships collected during Pass 1 upserts.
     *
     * Groups by relationship definition name, batch-looks up target entities by external ID,
     * and creates relationship links for resolved targets. Missing targets are skipped silently.
     * Individual relationship failures are caught and logged without aborting the sync.
     */
    private fun resolveRelationships(
        pending: List<RelationshipPending>,
        workspaceId: UUID,
        integrationId: UUID,
    ) {
        if (pending.isEmpty()) return

        val byDefinitionKey = pending.groupBy { it.relationshipDefinitionKey }

        for ((definitionKey, items) in byDefinitionKey) {
            try {
                val resolved = resolveDefinitionForKey(definitionKey, items, workspaceId) ?: continue
                val targetEntityMap = buildTargetEntityMap(items, workspaceId, integrationId)
                createMissingRelationships(items, resolved.definitionId, targetEntityMap, workspaceId)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to resolve relationships for definition '$definitionKey' — skipping group" }
            }
        }
    }

    /**
     * Looks up the source entity type and finds the matching relationship definition.
     *
     * @return Resolved definition ID and metadata, or null if not found.
     */
    private fun resolveDefinitionForKey(
        definitionKey: String,
        items: List<RelationshipPending>,
        workspaceId: UUID,
    ): ResolvedDefinition? {
        val sourceEntityId = items.first().sourceEntityId
        val sourceEntityTypeId = entityRepository.findById(sourceEntityId).orElse(null)?.typeId
        if (sourceEntityTypeId == null) {
            logger.warn { "Source entity $sourceEntityId not found — skipping definition '$definitionKey'" }
            return null
        }

        val definitions = relationshipDefinitionRepository
            .findByWorkspaceIdAndSourceEntityTypeId(workspaceId, sourceEntityTypeId)

        val definition = definitions.firstOrNull { it.name == definitionKey }
        if (definition == null) {
            logger.warn { "Relationship definition '$definitionKey' not found — skipping" }
            return null
        }

        val definitionId = requireNotNull(definition.id) { "RelationshipDefinitionEntity.id must not be null" }
        return ResolvedDefinition(definitionId)
    }

    /** Batch-fetches target entities by external ID and returns a lookup map. */
    private fun buildTargetEntityMap(
        items: List<RelationshipPending>,
        workspaceId: UUID,
        integrationId: UUID,
    ): Map<String, EntityEntity> {
        val allTargetExternalIds = items.flatMap { it.targetExternalIds }.distinct()
        return entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
            workspaceId, integrationId, allTargetExternalIds
        ).associateBy {
            requireNotNull(it.sourceExternalId) { "EntityEntity.sourceExternalId must not be null" }
        }
    }

    /** Dedup-checks and saves relationship entities for each pending item. */
    private fun createMissingRelationships(
        items: List<RelationshipPending>,
        definitionId: UUID,
        targetEntityMap: Map<String, EntityEntity>,
        workspaceId: UUID,
    ) {
        for (item in items) {
            try {
                val targetEntityIds = item.targetExternalIds.mapNotNull { extId ->
                    targetEntityMap[extId]?.id
                }
                if (targetEntityIds.isEmpty()) continue

                for (targetId in targetEntityIds) {
                    transactionTemplate.execute {
                        val existing = entityRelationshipRepository
                            .findBySourceIdAndTargetIdAndDefinitionId(
                                item.sourceEntityId, targetId, definitionId
                            )
                        if (existing.isEmpty()) {
                            entityRelationshipRepository.save(
                                EntityRelationshipEntity(
                                    workspaceId = workspaceId,
                                    sourceId = item.sourceEntityId,
                                    targetId = targetId,
                                    definitionId = definitionId,
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to resolve relationship for source=${item.sourceEntityId} — skipping" }
            }
        }
    }

    // ------ Helpers ------

    private fun buildFailureResult(entityTypeId: UUID?, message: String): SyncProcessingResult {
        logger.error { "fetchAndProcessRecords failed: $message" }
        return SyncProcessingResult(
            entityTypeId = entityTypeId,
            cursor = null,
            recordsSynced = 0,
            recordsFailed = 0,
            lastErrorMessage = message,
            success = false,
        )
    }

    // ------ Private Data Classes ------

    /** Resolved model context for a sync run. */
    private data class ModelContext(
        val entityTypeId: UUID,
        val typeKey: String,
        val identifierKey: UUID,
        val fieldMappings: Map<String, ResolvedFieldMapping>,
        val keyMapping: Map<String, UUID>,
        val externalIdField: String,
    )

    /** Aggregated result of processing a single batch of records. */
    private data class BatchResult(
        val synced: Int,
        val failed: Int,
        val lastError: String?,
        val syncedEntityIds: List<UUID> = emptyList(),
    )

    /** Resolved relationship definition metadata for Pass 2. */
    private data class ResolvedDefinition(
        val definitionId: UUID,
    )
}
