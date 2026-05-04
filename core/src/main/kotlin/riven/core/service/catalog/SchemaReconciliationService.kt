package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import riven.core.entity.catalog.CatalogEntityTypeEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.activity.Activity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.core.DataFormat
import riven.core.enums.core.DataType
import riven.core.enums.entity.validation.EntityTypeChangeType
import riven.core.enums.util.OperationType
import riven.core.models.catalog.EntityTypeHealthStatus
import riven.core.models.catalog.EntityTypeImpact
import riven.core.models.catalog.PendingSchemaChange
import riven.core.models.catalog.ReconciliationImpact
import riven.core.models.catalog.ReconciliationResult
import riven.core.models.catalog.ReconciledEntityType
import riven.core.models.catalog.SchemaChange
import riven.core.models.catalog.SchemaHealthResponse
import riven.core.models.catalog.SchemaHealthStatusType
import riven.core.models.catalog.SchemaHealthSummary
import riven.core.models.common.validation.Schema
import riven.core.models.entity.EntityTypeSchema
import riven.core.models.entity.configuration.ColumnConfiguration
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.enrichment.EnrichmentQueueService
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Compares workspace entity types against their catalog source definitions and
 * applies non-breaking changes automatically. Breaking changes are flagged with
 * `pendingSchemaUpdate = true` until explicitly confirmed via [applyBreakingChanges].
 *
 * Uses [EntityTypeEntity.attributeKeyMapping] as the bridge between string-keyed
 * catalog schemas and UUID-keyed workspace schemas.
 */
@Service
class SchemaReconciliationService(
    private val logger: KLogger,
    private val catalogEntityTypeRepository: CatalogEntityTypeRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val entityAttributeRepository: EntityAttributeRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val transactionManager: PlatformTransactionManager,
    private val enrichmentQueueService: EnrichmentQueueService,
) {

    /** Process-local concurrency guard keyed by entity type ID. */
    private val reconciliationLocks = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Each per-entity-type apply runs in its own physical transaction so a failure on
     * one entity rolls back only that iteration's writes — successful prior iterations
     * remain committed. REQUIRES_NEW is used because [applyAllChangesForEntityType] is a
     * private method on this bean, so an annotation-based propagation override would be
     * bypassed by the AOP proxy.
     */
    private val requiresNewTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    // ------ Public Reconciliation Entry Point ------

    /**
     * Reconciles workspace entity types against their catalog sources.
     * Called from EntityTypeService on workspace access. Skips entity types
     * that have no [EntityTypeEntity.sourceManifestId] or are already up to date.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun reconcileIfNeeded(workspaceId: UUID, entityTypes: List<EntityTypeEntity>) {
        val userId = authTokenService.getUserId()

        entityTypes
            .filter { it.sourceManifestId != null }
            .forEach { entityType -> reconcileSingle(entityType, userId, workspaceId) }
    }

    // ------ Breaking Change Application ------

    /**
     * Applies breaking schema changes for the given entity types after user confirmation.
     * If [impactConfirmed] is false, returns an impact analysis without mutating anything.
     * If true, applies all changes (breaking + non-breaking), clears pending flag, and logs activity.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun applyBreakingChanges(
        workspaceId: UUID,
        entityTypeIds: List<UUID>,
        impactConfirmed: Boolean,
    ): Any {
        val userId = authTokenService.getUserId()

        val entityTypes = entityTypeRepository.findAllById(entityTypeIds)
            .filter { it.workspaceId == workspaceId && it.pendingSchemaUpdate }

        if (!impactConfirmed) {
            return buildImpactAnalysis(entityTypes)
        }

        return applyConfirmedBreakingChanges(entityTypes, userId, workspaceId)
    }

    // ------ Health Check ------

    /**
     * Returns schema health status for all catalog-sourced entity types in the workspace.
     * Does NOT trigger reconciliation — read-only inspection.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getSchemaHealth(workspaceId: UUID, entityTypes: List<EntityTypeEntity>): SchemaHealthResponse {
        val statuses = entityTypes
            .filter { it.sourceManifestId != null }
            .map { buildHealthStatus(it) }

        return SchemaHealthResponse(
            entityTypes = statuses,
            summary = buildHealthSummary(statuses),
        )
    }

    // ------ Single Entity Type Reconciliation ------

    private fun reconcileSingle(entityType: EntityTypeEntity, userId: UUID, workspaceId: UUID) {
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity ID must not be null for reconciliation" }

        if (entityType.attributeKeyMapping == null) {
            logger.warn { "Skipping reconciliation for entity type $entityTypeId: no attributeKeyMapping (legacy)" }
            return
        }

        if (!reconciliationLocks.putIfAbsent(entityTypeId, true).let { it == null }) {
            logger.debug { "Reconciliation already in progress for entity type $entityTypeId, skipping" }
            return
        }

        try {
            val catalogEntry = lookupCatalogEntry(entityType) ?: return

            if (isUpToDate(entityType, catalogEntry)) {
                clearStalePendingFlag(entityType, userId, workspaceId)
                return
            }

            stampHashIfLegacy(entityType, catalogEntry)

            val changes = computeSchemaDiff(
                catalogSchema = catalogEntry.schema,
                workspaceSchema = entityType.schema,
                attributeKeyMapping = entityType.attributeKeyMapping!!,
            )

            val breakingChanges = changes.filter { it.breaking }
            val nonBreakingChanges = changes.filter { !it.breaking }

            if (nonBreakingChanges.isNotEmpty()) {
                applyNonBreakingChanges(entityType, nonBreakingChanges, catalogEntry.schema)
            }

            if (breakingChanges.isNotEmpty()) {
                handleBreakingWithNonBreakingApplied(
                    entityType, breakingChanges, nonBreakingChanges, userId, workspaceId,
                )
            } else {
                handleNoBreakingChanges(
                    entityType, catalogEntry, nonBreakingChanges, userId, workspaceId,
                )
            }

            if (nonBreakingChanges.isNotEmpty()) {
                invalidateConnotationSnapshots(entityTypeId, workspaceId, "non-breaking auto-apply")
            }
        } finally {
            reconciliationLocks.remove(entityTypeId)
        }
    }

    /**
     * Triggers re-enrichment for every entity of [entityTypeId] in [workspaceId] after a manifest
     * schema change has been applied. The STRUCTURAL metadata snapshots in `entity_connotation`
     * are now stale and need refreshing. Delegates to [EnrichmentQueueService.enqueueByEntityType]
     * which dedupes via the partial unique index on `execution_queue`.
     *
     * @param reason Free-form descriptor logged alongside the enqueue count for traceability.
     */
    private fun invalidateConnotationSnapshots(entityTypeId: UUID, workspaceId: UUID, reason: String) {
        val inserted = enrichmentQueueService.enqueueByEntityType(entityTypeId, workspaceId)
        logger.info {
            "Reconciliation enqueued $inserted ENRICHMENT items for entity type $entityTypeId ($reason)"
        }
    }

    private fun lookupCatalogEntry(entityType: EntityTypeEntity): CatalogEntityTypeEntity? {
        val manifestId = requireNotNull(entityType.sourceManifestId) { "sourceManifestId must not be null" }
        val entry = catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key)
        if (entry == null) {
            logger.debug { "No catalog entry found for manifest ${entityType.sourceManifestId}, key ${entityType.key}" }
        }
        return entry
    }

    private fun isUpToDate(entityType: EntityTypeEntity, catalogEntry: CatalogEntityTypeEntity): Boolean {
        val entityHash = entityType.sourceSchemaHash ?: return false
        return entityHash == catalogEntry.schemaHash
    }

    /**
     * If the entity type has no hash yet (legacy), stamp it with the current catalog hash
     * so future comparisons have a baseline.
     */
    private fun stampHashIfLegacy(entityType: EntityTypeEntity, catalogEntry: CatalogEntityTypeEntity) {
        if (entityType.sourceSchemaHash == null) {
            logger.info { "Stamping initial schema hash for entity type ${entityType.id}" }
        }
    }

    // ------ Schema Diff Engine ------

    /**
     * Compares a catalog schema (string-keyed) against a workspace schema (UUID-keyed)
     * using the attributeKeyMapping as the bridge. Returns a list of [SchemaChange]
     * describing every difference found.
     */
    fun computeSchemaDiff(
        catalogSchema: Map<String, Any>,
        workspaceSchema: EntityTypeSchema,
        attributeKeyMapping: Map<String, String>,
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()
        val workspaceProperties = workspaceSchema.properties ?: emptyMap()

        detectExistingFieldChanges(catalogSchema, workspaceProperties, attributeKeyMapping, changes)
        detectAddedFields(catalogSchema, attributeKeyMapping, changes)
        detectRemovedFields(catalogSchema, attributeKeyMapping, workspaceProperties, changes)

        return changes
    }

    private fun detectExistingFieldChanges(
        catalogSchema: Map<String, Any>,
        workspaceProperties: Map<UUID, Schema<UUID>>,
        attributeKeyMapping: Map<String, String>,
        changes: MutableList<SchemaChange>,
    ) {
        for ((stringKey, rawDef) in catalogSchema) {
            val uuidStr = attributeKeyMapping[stringKey] ?: continue
            val uuid = UUID.fromString(uuidStr)
            val workspaceAttr = workspaceProperties[uuid] ?: continue
            val catalogAttr = toStringMap(rawDef)

            detectTypeChange(stringKey, catalogAttr, workspaceAttr, uuid, changes)
            detectRequiredAdded(stringKey, catalogAttr, workspaceAttr, uuid, changes)
            detectUniqueAdded(stringKey, catalogAttr, workspaceAttr, uuid, changes)
            detectMetadataChange(stringKey, catalogAttr, workspaceAttr, uuid, changes)
        }
    }

    private fun detectTypeChange(
        key: String,
        catalogAttr: Map<String, Any>,
        workspaceAttr: Schema<UUID>,
        uuid: UUID,
        changes: MutableList<SchemaChange>,
    ) {
        val catalogKey = (catalogAttr["key"] as? String)?.uppercase() ?: return
        if (catalogKey != workspaceAttr.key.name) {
            changes.add(
                SchemaChange(
                    type = EntityTypeChangeType.FIELD_TYPE_CHANGED,
                    attributeKey = key,
                    description = "Type changed from ${workspaceAttr.key.name} to $catalogKey",
                    breaking = true,
                    workspaceAttributeId = uuid,
                )
            )
        }
    }

    private fun detectRequiredAdded(
        key: String,
        catalogAttr: Map<String, Any>,
        workspaceAttr: Schema<UUID>,
        uuid: UUID,
        changes: MutableList<SchemaChange>,
    ) {
        val catalogRequired = catalogAttr["required"] as? Boolean ?: false
        if (catalogRequired && !workspaceAttr.required) {
            changes.add(
                SchemaChange(
                    type = EntityTypeChangeType.FIELD_REQUIRED_ADDED,
                    attributeKey = key,
                    description = "Field '$key' changed from optional to required",
                    breaking = true,
                    workspaceAttributeId = uuid,
                )
            )
        }
    }

    private fun detectUniqueAdded(
        key: String,
        catalogAttr: Map<String, Any>,
        workspaceAttr: Schema<UUID>,
        uuid: UUID,
        changes: MutableList<SchemaChange>,
    ) {
        val catalogUnique = catalogAttr["unique"] as? Boolean ?: false
        if (catalogUnique && !workspaceAttr.unique) {
            changes.add(
                SchemaChange(
                    type = EntityTypeChangeType.FIELD_UNIQUE_ADDED,
                    attributeKey = key,
                    description = "Field '$key' changed from non-unique to unique",
                    breaking = true,
                    workspaceAttributeId = uuid,
                )
            )
        }
    }

    private fun detectMetadataChange(
        key: String,
        catalogAttr: Map<String, Any>,
        workspaceAttr: Schema<UUID>,
        uuid: UUID,
        changes: MutableList<SchemaChange>,
    ) {
        val diffs = mutableListOf<String>()

        val catalogLabel = catalogAttr["label"] as? String
        if (catalogLabel != null && catalogLabel != workspaceAttr.label) {
            diffs.add("label: '${workspaceAttr.label}' -> '$catalogLabel'")
        }

        val catalogFormat = catalogAttr["format"] as? String
        val workspaceFormat = workspaceAttr.format?.jsonValue
        if (catalogFormat != workspaceFormat) {
            diffs.add("format: '$workspaceFormat' -> '$catalogFormat'")
        }

        if (diffs.isNotEmpty()) {
            changes.add(
                SchemaChange(
                    type = EntityTypeChangeType.METADATA_CHANGED,
                    attributeKey = key,
                    description = "Metadata changed for '$key': ${diffs.joinToString(", ")}",
                    breaking = false,
                    workspaceAttributeId = uuid,
                )
            )
        }
    }

    private fun detectAddedFields(
        catalogSchema: Map<String, Any>,
        attributeKeyMapping: Map<String, String>,
        changes: MutableList<SchemaChange>,
    ) {
        for (stringKey in catalogSchema.keys) {
            if (stringKey !in attributeKeyMapping) {
                changes.add(
                    SchemaChange(
                        type = EntityTypeChangeType.FIELD_ADDED,
                        attributeKey = stringKey,
                        description = "New field '$stringKey' added in catalog",
                        breaking = false,
                    )
                )
            }
        }
    }

    private fun detectRemovedFields(
        catalogSchema: Map<String, Any>,
        attributeKeyMapping: Map<String, String>,
        workspaceProperties: Map<UUID, Schema<UUID>>,
        changes: MutableList<SchemaChange>,
    ) {
        for ((stringKey, uuidStr) in attributeKeyMapping) {
            if (stringKey !in catalogSchema) {
                val uuid = UUID.fromString(uuidStr)
                val label = workspaceProperties[uuid]?.label ?: stringKey
                changes.add(
                    SchemaChange(
                        type = EntityTypeChangeType.FIELD_REMOVED,
                        attributeKey = stringKey,
                        description = "Field '$label' ($stringKey) removed from catalog",
                        breaking = true,
                        workspaceAttributeId = uuid,
                    )
                )
            }
        }
    }

    // ------ Reconciliation Outcome Handlers ------

    /**
     * Handles the case where breaking changes exist alongside (already-applied) non-breaking ones.
     * Only logs activity on first detection — skips re-logging if [pendingSchemaUpdate] is already set.
     * Does NOT update [sourceSchemaHash] because the workspace hasn't accepted the breaking changes.
     */
    private fun handleBreakingWithNonBreakingApplied(
        entityType: EntityTypeEntity,
        breakingChanges: List<SchemaChange>,
        nonBreakingChanges: List<SchemaChange>,
        userId: UUID,
        workspaceId: UUID,
    ) {
        val alreadyPending = entityType.pendingSchemaUpdate

        entityType.pendingSchemaUpdate = true
        entityTypeRepository.save(entityType)

        if (!alreadyPending) {
            logReconciliationActivity(
                entityType = entityType,
                operation = OperationType.UPDATE,
                userId = userId,
                workspaceId = workspaceId,
                details = mapOf(
                    "action" to "BREAKING_DETECTED",
                    "breakingChanges" to breakingChanges.map { it.attributeKey },
                    "nonBreakingApplied" to nonBreakingChanges.size,
                    "totalChanges" to breakingChanges.size + nonBreakingChanges.size,
                ),
            )

            logger.info {
                "Breaking changes detected for entity type ${entityType.id}: " +
                    breakingChanges.joinToString { it.attributeKey }
            }
        } else {
            if (nonBreakingChanges.isNotEmpty()) {
                logger.info {
                    "Auto-applied ${nonBreakingChanges.size} non-breaking changes for entity type ${entityType.id} " +
                        "(breaking changes still pending)"
                }
            }
        }
    }

    /**
     * Handles the case where no breaking changes remain. If [pendingSchemaUpdate] was previously
     * set (a prior breaking change was resolved by a subsequent catalog update), clears the flag
     * and logs the resolution. Updates [sourceSchemaHash] to mark the workspace as current.
     */
    private fun handleNoBreakingChanges(
        entityType: EntityTypeEntity,
        catalogEntry: CatalogEntityTypeEntity,
        nonBreakingChanges: List<SchemaChange>,
        userId: UUID,
        workspaceId: UUID,
    ) {
        val wasBreakingPending = entityType.pendingSchemaUpdate

        if (wasBreakingPending) {
            entityType.pendingSchemaUpdate = false
        }

        entityType.sourceSchemaHash = catalogEntry.schemaHash
        entityTypeRepository.save(entityType)

        if (wasBreakingPending) {
            logReconciliationActivity(
                entityType = entityType,
                operation = OperationType.UPDATE,
                userId = userId,
                workspaceId = workspaceId,
                details = mapOf(
                    "action" to "BREAKING_RESOLVED",
                    "nonBreakingApplied" to nonBreakingChanges.size,
                ),
            )
            logger.info { "Breaking changes resolved for entity type ${entityType.id} by catalog update" }
        } else if (nonBreakingChanges.isNotEmpty()) {
            logReconciliationActivity(
                entityType = entityType,
                operation = OperationType.UPDATE,
                userId = userId,
                workspaceId = workspaceId,
                details = mapOf(
                    "action" to "AUTO_APPLY",
                    "changesApplied" to nonBreakingChanges.size,
                    "changeTypes" to nonBreakingChanges.map { it.type.name }.distinct(),
                ),
            )
            logger.info { "Auto-applied ${nonBreakingChanges.size} non-breaking changes to entity type ${entityType.id}" }
        }
    }

    /**
     * Clears a stale [pendingSchemaUpdate] flag when the workspace schema hash already matches
     * the catalog. This happens when a breaking change is reversed by a subsequent catalog update
     * that produces an identical schema to what the workspace already has.
     */
    private fun clearStalePendingFlag(entityType: EntityTypeEntity, userId: UUID, workspaceId: UUID) {
        if (!entityType.pendingSchemaUpdate) return

        entityType.pendingSchemaUpdate = false
        entityTypeRepository.save(entityType)

        logReconciliationActivity(
            entityType = entityType,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            details = mapOf("action" to "BREAKING_RESOLVED"),
        )
        logger.info { "Cleared stale pendingSchemaUpdate for entity type ${entityType.id} (hashes match)" }
    }

    /**
     * Applies non-breaking changes (FIELD_ADDED, METADATA_CHANGED) to the workspace entity type.
     */
    private fun applyNonBreakingChanges(
        entityType: EntityTypeEntity,
        changes: List<SchemaChange>,
        catalogSchema: Map<String, Any>,
    ) {
        val mutableProperties = entityType.schema.properties?.toMutableMap() ?: mutableMapOf()
        val mutableMapping = entityType.attributeKeyMapping?.toMutableMap() ?: mutableMapOf()
        val mutableColumnOrder = entityType.columnConfiguration?.order?.toMutableList() ?: mutableListOf()
        val columnOverrides = entityType.columnConfiguration?.overrides ?: emptyMap()

        for (change in changes) {
            when (change.type) {
                EntityTypeChangeType.FIELD_ADDED -> applyFieldAdded(
                    change, entityType.key, catalogSchema, mutableProperties, mutableMapping, mutableColumnOrder,
                )
                EntityTypeChangeType.METADATA_CHANGED -> applyMetadataChanged(
                    change, catalogSchema, mutableProperties,
                )
                else -> {} // Non-breaking filter should prevent other types
            }
        }

        entityType.schema = entityType.schema.copy(properties = mutableProperties)
        entityType.attributeKeyMapping = mutableMapping
        entityType.columnConfiguration = ColumnConfiguration(order = mutableColumnOrder, overrides = columnOverrides)
    }

    private fun applyFieldAdded(
        change: SchemaChange,
        entityTypeKey: String,
        catalogSchema: Map<String, Any>,
        properties: MutableMap<UUID, Schema<UUID>>,
        mapping: MutableMap<String, String>,
        columnOrder: MutableList<UUID>,
    ) {
        val catalogAttr = toStringMap(catalogSchema[change.attributeKey] ?: return)
        val uuid = generateAttributeUuid(entityTypeKey, change.attributeKey)

        properties[uuid] = buildAttributeSchema(catalogAttr)
        mapping[change.attributeKey] = uuid.toString()
        columnOrder.add(uuid)
    }

    private fun applyMetadataChanged(
        change: SchemaChange,
        catalogSchema: Map<String, Any>,
        properties: MutableMap<UUID, Schema<UUID>>,
    ) {
        val uuid = requireNotNull(change.workspaceAttributeId) { "workspaceAttributeId required for METADATA_CHANGED" }
        val catalogAttr = toStringMap(catalogSchema[change.attributeKey] ?: return)
        val existing = properties[uuid] ?: return

        properties[uuid] = existing.copy(
            label = catalogAttr["label"] as? String ?: existing.label,
            format = parseDataFormat(catalogAttr["format"] as? String) ?: existing.format,
        )
    }

    // ------ Breaking Change Application ------

    private fun buildImpactAnalysis(entityTypes: List<EntityTypeEntity>): ReconciliationImpact {
        val impacts = entityTypes.associate { entityType ->
            val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity ID must not be null" }
            val catalogEntry = lookupCatalogEntry(entityType)
            val mapping = entityType.attributeKeyMapping ?: emptyMap()

            val impact = if (catalogEntry != null) {
                val changes = computeSchemaDiff(catalogEntry.schema, entityType.schema, mapping)
                buildEntityTypeImpact(entityTypeId, changes)
            } else {
                EntityTypeImpact(affectedEntities = 0, fieldsRemoved = emptyList(), dataLoss = false)
            }

            entityTypeId to impact
        }

        return ReconciliationImpact(impacts = impacts)
    }

    private fun buildEntityTypeImpact(entityTypeId: UUID, changes: List<SchemaChange>): EntityTypeImpact {
        val removedFields = changes
            .filter { it.type == EntityTypeChangeType.FIELD_REMOVED }
            .map { it.attributeKey }

        val affectedEntities = changes
            .filter { it.workspaceAttributeId != null && it.breaking }
            .sumOf { change ->
                entityAttributeRepository.countByTypeIdAndAttributeId(
                    entityTypeId, requireNotNull(change.workspaceAttributeId),
                )
            }

        return EntityTypeImpact(
            affectedEntities = affectedEntities,
            fieldsRemoved = removedFields,
            dataLoss = removedFields.isNotEmpty(),
        )
    }

    private fun applyConfirmedBreakingChanges(
        entityTypes: List<EntityTypeEntity>,
        userId: UUID,
        workspaceId: UUID,
    ): ReconciliationResult {
        val reconciled = mutableListOf<ReconciledEntityType>()
        val errors = mutableListOf<String>()

        for (entityType in entityTypes) {
            try {
                val result = requiresNewTemplate.execute { applyAllChangesForEntityType(entityType) }
                    ?: error("TransactionTemplate.execute returned null for entity type ${entityType.id}")
                reconciled.add(result)

                logReconciliationActivity(
                    entityType = entityType,
                    operation = OperationType.UPDATE,
                    userId = userId,
                    workspaceId = workspaceId,
                    details = mapOf(
                        "action" to "BREAKING_APPLIED",
                        "changesApplied" to result.changesApplied,
                        "breakingChangesApplied" to result.breakingChangesApplied,
                    ),
                )

                invalidateConnotationSnapshots(result.entityTypeId, workspaceId, "breaking confirmed")
            } catch (e: Exception) {
                val entityTypeId = entityType.id?.toString() ?: "unknown"
                logger.error(e) { "Failed to apply breaking changes for entity type $entityTypeId" }
                errors.add("Entity type $entityTypeId: ${e.message}")
            }
        }

        return ReconciliationResult(reconciled = reconciled, errors = errors)
    }

    private fun applyAllChangesForEntityType(entityType: EntityTypeEntity): ReconciledEntityType {
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity ID must not be null" }
        val catalogEntry = requireNotNull(lookupCatalogEntry(entityType)) {
            "Catalog entry not found for entity type $entityTypeId"
        }
        val mapping = requireNotNull(entityType.attributeKeyMapping) {
            "attributeKeyMapping must not be null for reconciliation"
        }

        val changes = computeSchemaDiff(catalogEntry.schema, entityType.schema, mapping)

        applyNonBreakingChanges(entityType, changes.filter { !it.breaking }, catalogEntry.schema)
        applyBreakingFieldChanges(entityType, changes.filter { it.breaking }, catalogEntry.schema)

        entityType.sourceSchemaHash = catalogEntry.schemaHash
        entityType.pendingSchemaUpdate = false
        entityTypeRepository.save(entityType)

        return ReconciledEntityType(
            entityTypeId = entityTypeId,
            changesApplied = changes.size,
            breakingChangesApplied = changes.count { it.breaking },
        )
    }

    /**
     * Applies breaking changes: FIELD_REMOVED deletes attribute data, FIELD_TYPE_CHANGED
     * updates the schema type, FIELD_REQUIRED_ADDED/FIELD_UNIQUE_ADDED update constraints.
     */
    private fun applyBreakingFieldChanges(
        entityType: EntityTypeEntity,
        changes: List<SchemaChange>,
        catalogSchema: Map<String, Any>,
    ) {
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity ID must not be null" }
        val mutableProperties = entityType.schema.properties?.toMutableMap() ?: mutableMapOf()
        val mutableMapping = entityType.attributeKeyMapping?.toMutableMap() ?: mutableMapOf()
        val mutableColumnOrder = entityType.columnConfiguration?.order?.toMutableList() ?: mutableListOf()
        val columnOverrides = entityType.columnConfiguration?.overrides?.toMutableMap() ?: mutableMapOf()

        for (change in changes) {
            val uuid = change.workspaceAttributeId ?: continue

            when (change.type) {
                EntityTypeChangeType.FIELD_REMOVED -> {
                    entityAttributeRepository.deleteAllByTypeIdAndAttributeId(entityTypeId, uuid)
                    mutableProperties.remove(uuid)
                    mutableMapping.remove(change.attributeKey)
                    mutableColumnOrder.remove(uuid)
                    columnOverrides.remove(uuid)
                }

                EntityTypeChangeType.FIELD_TYPE_CHANGED -> {
                    val catalogAttr = toStringMap(catalogSchema[change.attributeKey] ?: continue)
                    val existing = mutableProperties[uuid] ?: continue
                    mutableProperties[uuid] = existing.copy(
                        key = parseSchemaType(catalogAttr["key"] as? String),
                        type = parseDataType(catalogAttr["type"] as? String),
                        format = parseDataFormat(catalogAttr["format"] as? String),
                    )
                }

                EntityTypeChangeType.FIELD_REQUIRED_ADDED -> {
                    val existing = mutableProperties[uuid] ?: continue
                    mutableProperties[uuid] = existing.copy(required = true)
                }

                EntityTypeChangeType.FIELD_UNIQUE_ADDED -> {
                    val existing = mutableProperties[uuid] ?: continue
                    mutableProperties[uuid] = existing.copy(unique = true)
                }

                else -> {}
            }
        }

        entityType.schema = entityType.schema.copy(properties = mutableProperties)
        entityType.attributeKeyMapping = mutableMapping
        entityType.columnConfiguration = ColumnConfiguration(order = mutableColumnOrder, overrides = columnOverrides)
    }

    // ------ Health Status ------

    private fun buildHealthStatus(entityType: EntityTypeEntity): EntityTypeHealthStatus {
        val catalogEntry = lookupCatalogEntry(entityType)
        if (catalogEntry == null || entityType.attributeKeyMapping == null) {
            return unknownStatus(entityType)
        }
        if (entityType.sourceSchemaHash == catalogEntry.schemaHash && !entityType.pendingSchemaUpdate) {
            return upToDateStatus(entityType, catalogEntry)
        }
        val changes = computeSchemaDiff(catalogEntry.schema, entityType.schema, entityType.attributeKeyMapping!!)
        if (changes.isEmpty()) {
            return upToDateStatus(entityType, catalogEntry, sourceHashOverride = catalogEntry.schemaHash)
        }
        return pendingStatus(entityType, catalogEntry, changes)
    }

    private fun unknownStatus(entityType: EntityTypeEntity): EntityTypeHealthStatus {
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity ID must not be null" }
        return EntityTypeHealthStatus(
            entityTypeId = entityTypeId,
            entityTypeKey = entityType.key,
            displayName = entityType.displayNameSingular,
            status = SchemaHealthStatusType.UNKNOWN,
            sourceSchemaHash = entityType.sourceSchemaHash,
            catalogSchemaHash = null,
            pendingChanges = emptyList(),
        )
    }

    /**
     * Builds the UP_TO_DATE response. Used by both the hash-match fast path (override = null →
     * reports the entity's stored hash) and the empty-diff legacy path where the entity is
     * structurally identical to catalog but its stored hash hasn't been stamped yet
     * (override = catalogEntry.schemaHash so the response reflects equivalence rather than hash
     * provenance — actual stamp happens in reconcileIfNeeded).
     */
    private fun upToDateStatus(
        entityType: EntityTypeEntity,
        catalogEntry: CatalogEntityTypeEntity,
        sourceHashOverride: String? = null,
    ): EntityTypeHealthStatus {
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity ID must not be null" }
        return EntityTypeHealthStatus(
            entityTypeId = entityTypeId,
            entityTypeKey = entityType.key,
            displayName = entityType.displayNameSingular,
            status = SchemaHealthStatusType.UP_TO_DATE,
            sourceSchemaHash = sourceHashOverride ?: entityType.sourceSchemaHash,
            catalogSchemaHash = catalogEntry.schemaHash,
            pendingChanges = emptyList(),
        )
    }

    private fun pendingStatus(
        entityType: EntityTypeEntity,
        catalogEntry: CatalogEntityTypeEntity,
        changes: List<SchemaChange>,
    ): EntityTypeHealthStatus {
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity ID must not be null" }
        val hasBreaking = changes.any { it.breaking }
        return EntityTypeHealthStatus(
            entityTypeId = entityTypeId,
            entityTypeKey = entityType.key,
            displayName = entityType.displayNameSingular,
            status = if (hasBreaking) SchemaHealthStatusType.PENDING_BREAKING else SchemaHealthStatusType.PENDING_NON_BREAKING,
            sourceSchemaHash = entityType.sourceSchemaHash,
            catalogSchemaHash = catalogEntry.schemaHash,
            pendingChanges = computePendingChangesWithCounts(entityTypeId, changes),
        )
    }

    private fun computePendingChangesWithCounts(
        entityTypeId: UUID,
        changes: List<SchemaChange>,
    ): List<PendingSchemaChange> = changes.map { change ->
        val affectedCount = if (change.workspaceAttributeId != null) {
            entityAttributeRepository.countByTypeIdAndAttributeId(entityTypeId, change.workspaceAttributeId)
        } else {
            0L
        }
        PendingSchemaChange(
            type = change.type,
            attributeKey = change.attributeKey,
            description = change.description,
            breaking = change.breaking,
            affectedEntityCount = affectedCount,
        )
    }

    private fun buildHealthSummary(statuses: List<EntityTypeHealthStatus>): SchemaHealthSummary {
        return SchemaHealthSummary(
            total = statuses.size,
            upToDate = statuses.count { it.status == SchemaHealthStatusType.UP_TO_DATE },
            pendingNonBreaking = statuses.count { it.status == SchemaHealthStatusType.PENDING_NON_BREAKING },
            pendingBreaking = statuses.count { it.status == SchemaHealthStatusType.PENDING_BREAKING },
            unknown = statuses.count { it.status == SchemaHealthStatusType.UNKNOWN },
        )
    }

    // ------ Activity Logging ------

    private fun logReconciliationActivity(
        entityType: EntityTypeEntity,
        operation: OperationType,
        userId: UUID,
        workspaceId: UUID,
        details: Map<String, Any?>,
    ) {
        activityService.logActivity(
            activity = Activity.ENTITY_TYPE,
            operation = operation,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.ENTITY_TYPE,
            entityId = entityType.id,
            details = details,
        )
    }

    // ------ Schema Building Helpers ------

    /**
     * Generates a deterministic UUID v3 from the entity type key and attribute key.
     * Uses the same namespace convention as [TemplateMaterializationService.generateAttributeUuid]
     * with "integration" as the prefix for catalog-sourced attributes.
     */
    private fun generateAttributeUuid(entityTypeKey: String, attributeKey: String): UUID {
        return UUID.nameUUIDFromBytes("integration:$entityTypeKey:$attributeKey".toByteArray())
    }

    /**
     * Builds a workspace [Schema] from a catalog attribute definition map.
     * Mirrors the logic in [TemplateMaterializationService.buildAttributeSchema].
     */
    private fun buildAttributeSchema(attributeDef: Map<String, Any>): Schema<UUID> {
        val schemaType = parseSchemaType(attributeDef["key"] as? String)
        val dataType = parseDataType(attributeDef["type"] as? String)
        val dataFormat = parseDataFormat(attributeDef["format"] as? String)

        return Schema(
            label = attributeDef["label"] as? String,
            key = schemaType,
            type = dataType,
            format = dataFormat,
            required = attributeDef["required"] as? Boolean ?: false,
            unique = attributeDef["unique"] as? Boolean ?: false,
            `protected` = attributeDef["protected"] as? Boolean ?: false,
        )
    }

    // ------ Parsing Helpers ------

    private fun parseSchemaType(value: String?): SchemaType {
        if (value == null) return SchemaType.TEXT
        return try {
            SchemaType.valueOf(value.uppercase())
        } catch (_: Exception) {
            SchemaType.TEXT
        }
    }

    private fun parseDataType(value: String?): DataType {
        if (value == null) return DataType.STRING
        return DataType.entries.find { it.jsonValue == value } ?: DataType.STRING
    }

    private fun parseDataFormat(value: String?): DataFormat? {
        if (value == null) return null
        return DataFormat.entries.find { it.jsonValue == value }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toStringMap(value: Any): Map<String, Any> {
        return value as? Map<String, Any> ?: emptyMap()
    }
}
