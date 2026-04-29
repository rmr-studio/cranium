package riven.core.service.entity

import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.activity.ActivityLogEntity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.integration.SourceType
import riven.core.enums.util.OperationType
import riven.core.exceptions.NotFoundException
import riven.core.exceptions.SchemaValidationException
import riven.core.models.common.Icon
import riven.core.models.common.validation.Schema
import riven.core.models.entity.Entity
import riven.core.models.entity.EntityLink
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRelationPayloadReference
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.entity.query.EntityQuery
import riven.core.models.entity.query.pagination.QueryPagination
import riven.core.models.request.entity.DeleteEntityRequest
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.models.response.entity.DeleteEntityResponse
import riven.core.models.response.entity.SaveEntityResponse
import riven.core.enums.entity.EntitySelectType
import riven.core.service.entity.query.EntityQueryService
import kotlinx.coroutines.runBlocking
import riven.core.repository.entity.EntityRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.models.common.validation.DefaultValue
import riven.core.service.entity.type.EntityTypeAttributeService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.entity.type.EntityTypeSequenceService
import riven.core.service.entity.type.EntityTypeService
import riven.core.models.identity.IdentityMatchTriggerEvent
import riven.core.models.websocket.EntityEvent
import riven.core.service.identity.EntityTypeClassificationService
import riven.core.util.ServiceUtil.findManyResults
import riven.core.util.ServiceUtil.findOrThrow
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

/**
 * Service for managing entity instances.
 */
@Service
class EntityService(
    private val entityRepository: EntityRepository,
    private val entityTypeService: EntityTypeService,
    private val entityRelationshipService: EntityRelationshipService,
    private val entityTypeRelationshipService: EntityTypeRelationshipService,
    private val entityValidationService: EntityValidationService,
    private val entityTypeAttributeService: EntityTypeAttributeService,
    private val entityAttributeService: EntityAttributeService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val sequenceService: EntityTypeSequenceService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val entityQueryService: EntityQueryService,
    private val entityTypeClassificationService: EntityTypeClassificationService,
) {


    @PostAuthorize("@workspaceSecurity.hasWorkspace(returnObject.workspaceId)")
    fun getEntity(id: UUID): Entity {
        val entity = findOrThrow { entityRepository.findById(id) }
        val relationships = entityRelationshipService.findRelatedEntities(
            entityId = id,
            workspaceId = entity.workspaceId
        )
        val attributes = entityAttributeService.getAttributes(id)

        return entity.toModel(relationships = relationships, attributes = attributes)
    }

    fun getEntitiesByIds(ids: Set<UUID>): List<EntityEntity> {
        return findManyResults { entityRepository.findAllById(ids) }
    }

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getEntitiesByTypeId(
        workspaceId: UUID,
        typeId: UUID
    ): List<Entity> {
        val entities = findManyResults {
            entityRepository.findByTypeId(typeId)
        }

        require(entities.all { it.workspaceId == workspaceId }) { "One or more entities do not belong to the specified workspace" }
        val entityIds = entities.mapNotNull { it.id }.toSet()
        val relationships = entityRelationshipService.findRelatedEntities(
            entityIds = entityIds,
            workspaceId = workspaceId
        )
        val allAttributes = entityAttributeService.getAttributesForEntities(entityIds)

        return entities.map {
            val id = requireNotNull(it.id)
            it.toModel(
                audit = true,
                relationships = relationships[id] ?: emptyMap(),
                attributes = allAttributes[id] ?: emptyMap(),
            )
        }
    }

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getEntitiesByTypeIds(
        workspaceId: UUID,
        typeIds: List<UUID>
    ): Map<UUID, List<Entity>> {
        val entities = findManyResults {
            entityRepository.findByTypeIdIn(
                typeIds = typeIds
            )
        }

        require(entities.all { it.workspaceId == workspaceId }) { "One or more entities do not belong to the specified workspace" }

        val entityIds = entities.mapNotNull { it.id }.toSet()
        val relationships = entityRelationshipService.findRelatedEntities(
            entityIds = entityIds,
            workspaceId = workspaceId
        )
        val allAttributes = entityAttributeService.getAttributesForEntities(entityIds)

        return entities.map {
            val id = requireNotNull(it.id)
            it.toModel(
                audit = true,
                relationships = relationships[id] ?: emptyMap(),
                attributes = allAttributes[id] ?: emptyMap(),
            )
        }.groupBy { it.typeId }

    }

    /**
     * Create or update an entity with validation.
     * If request.id is provided, updates the existing entity; otherwise creates a new one.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun saveEntity(
        workspaceId: UUID,
        entityTypeId: UUID,
        request: SaveEntityRequest,
    ): SaveEntityResponse {
        try {
            val (id: UUID?, payload: Map<UUID, EntityAttributeRequest>, icon: Icon?) = request
            val userId = authTokenService.getUserId()
            val type: EntityTypeEntity = entityTypeService.getById(entityTypeId)
            val typeId = requireNotNull(type.id) { "Entity type ID cannot be null" }

            // Check if this is an update (existing entity) or create (new entity)
            val prev: EntityEntity? = id?.let { findOrThrow { entityRepository.findById(it) } }

            val attributePayload: Map<UUID, EntityAttributePrimitivePayload> = payload.mapNotNull { (key, value) ->
                key to value.payload.let {
                    when (it) {
                        is EntityAttributePrimitivePayload -> it
                        else -> return@mapNotNull null
                    }
                }
            }.toMap()

            val relationshipPayload: Map<UUID, List<UUID>> = payload.mapNotNull { (key, value) ->
                when (val pl = value.payload) {
                    is EntityAttributeRelationPayloadReference -> {
                        key to pl.relations
                    }

                    else -> return@mapNotNull null
                }
            }.toMap()

            prev?.run {
                require(this.workspaceId == workspaceId) { "Entity does not belong to the specified workspace" }
                require(this.typeId == entityTypeId) { "Entity type cannot be changed" }
            }

            // Load previous attributes for updates
            val previousAttributes = if (prev != null) entityAttributeService.getAttributes(requireNotNull(prev.id)) else emptyMap()

            // Inject defaults and generate IDs
            val enrichedPayload = injectDefaultsAndGenerateIds(
                attributePayload = attributePayload,
                schema = type.schema,
                entityTypeId = typeId,
                isCreate = prev == null,
                previousAttributes = previousAttributes,
            )

            // Either update the existing entity or create a new one
            val entity = prev.let {
                if (it != null) {
                    return@let it.copy(
                        iconType = icon?.type ?: it.iconType,
                        iconColour = icon?.colour ?: it.iconColour,
                    )
                }

                EntityEntity(
                    workspaceId = workspaceId,
                    typeId = entityTypeId,
                    typeKey = type.key,
                    iconType = icon?.type ?: type.iconType,
                    iconColour = icon?.colour ?: type.iconColour,
                    identifierKey = type.identifierKey,
                )
            }

            // Validate payload against schema
            entityValidationService.validateEntity(
                entity,
                type,
                attributes = enrichedPayload,
                isUpdate = prev != null,
                previousAttributes = previousAttributes,
            ).run {
                if (isNotEmpty()) {
                    throw SchemaValidationException(this)
                }
            }

            return entityRepository.save(entity).run {
                val entityId = requireNotNull(this.id) { "Saved entity ID cannot be null" }

                // Save normalized attributes
                entityAttributeService.saveAttributes(
                    entityId = entityId,
                    workspaceId = workspaceId,
                    typeId = typeId,
                    attributes = enrichedPayload,
                )

                // Handle Unique Constraints
                val uniqueAttributes = entityTypeAttributeService.extractUniqueAttributes(type, payload)

                val uniqueValuesToSave = uniqueAttributes
                    .filterValues { it != null }
                    .mapNotNull { (fieldId, value) ->
                        if (value == null) return@mapNotNull null
                        entityTypeAttributeService.checkAttributeUniqueness(
                            typeId = typeId,
                            fieldId = fieldId,
                            value = value.value,
                            excludeEntityId = entityId
                        )
                        fieldId to value.value.toString()
                    }
                    .toMap()

                entityTypeAttributeService.saveUniqueValues(
                    workspaceId = workspaceId,
                    entityId = entityId,
                    typeId = typeId,
                    uniqueValues = uniqueValuesToSave
                )

                // Save relationships per definition
                saveRelationshipsPerDefinition(entityId, workspaceId, entityTypeId, relationshipPayload)

                activityService.log(
                    activity = Activity.ENTITY,
                    operation = if (prev != null) OperationType.UPDATE else OperationType.CREATE,
                    userId = userId,
                    workspaceId = workspaceId,
                    entityType = ApplicationEntityType.ENTITY,
                    entityId = this.id,
                    "type" to type.key,
                    "category" to type.displayNameSingular
                )

                applicationEventPublisher.publishEvent(
                    EntityEvent(
                        workspaceId = workspaceId,
                        userId = userId,
                        operation = if (prev != null) OperationType.UPDATE else OperationType.CREATE,
                        entityId = this.id,
                        entityTypeId = typeId,
                        entityTypeKey = type.key,
                        summary = mapOf(
                            "entityTypeName" to type.displayNameSingular,
                            "userDisplayName" to authTokenService.getUserDisplayName(),
                        ),
                    )
                )

                publishIdentityMatchTriggerEvent(
                    entityId = entityId,
                    workspaceId = workspaceId,
                    typeId = typeId,
                    isUpdate = prev != null,
                    previousAttributes = previousAttributes,
                    enrichedPayload = enrichedPayload,
                )

                // Reload links after save
                val links: Map<UUID, List<EntityLink>> = entityRelationshipService.findRelatedEntities(entityId, workspaceId)

                SaveEntityResponse(
                    entity = this.toModel(
                        audit = true,
                        relationships = links,
                        attributes = enrichedPayload,
                    ),
                    impactedEntities = null
                )
            }
        } catch (e: SchemaValidationException) {
            return SaveEntityResponse(
                errors = e.reasons
            )
        } catch (e: Exception) {
            throw e
        }
    }

    // ------ Internal entry points (system-driven, no JWT) ------

    /**
     * System-driven entity upsert. Bypasses [PreAuthorize] and the JWT-based user lookup;
     * called from background contexts such as Temporal activities and the knowledge ingestion
     * layer ([riven.core.service.knowledge.AbstractKnowledgeEntityIngestionService]).
     *
     * Differences from [saveEntity]:
     *   - accepts a flat `Map<UUID, Any?>` payload pre-resolved to attribute UUIDs;
     *   - supports upsert via [existingId] (looked up under the workspace);
     *   - threads provenance fields ([sourceType], [sourceIntegrationId], [sourceExternalId])
     *     and [readonly] onto the persisted [EntityEntity];
     *   - skips identity-match event emission and activity logging — the caller owns those
     *     when they apply (see ingestion services for the activity write path).
     *
     * Schema validation, default injection, ID generation, and unique-value enforcement still
     * run.
     */
    @Transactional
    fun saveEntityInternal(
        workspaceId: UUID,
        entityTypeId: UUID,
        existingId: UUID?,
        attributePayload: Map<UUID, Any?>,
        sourceType: SourceType = SourceType.USER_CREATED,
        sourceIntegrationId: UUID? = null,
        sourceExternalId: String? = null,
        readonly: Boolean = false,
    ): EntityEntity {
        val type: EntityTypeEntity = entityTypeService.getById(entityTypeId)
        val typeId = requireNotNull(type.id) { "Entity type ID cannot be null" }

        val prev: EntityEntity? = existingId?.let { findOrThrow { entityRepository.findById(it) } }
        prev?.run {
            require(this.workspaceId == workspaceId) { "Entity does not belong to the specified workspace" }
            require(this.typeId == entityTypeId) { "Entity type cannot be changed" }
        }

        val previousAttributes = if (prev != null) entityAttributeService.getAttributes(requireNotNull(prev.id)) else emptyMap()

        // Wrap raw values into EntityAttributePrimitivePayload using each attribute's declared schemaType.
        val primitivePayload: Map<UUID, EntityAttributePrimitivePayload> = attributePayload
            .mapNotNull { (attrId, raw) ->
                if (raw == null) return@mapNotNull null
                val schemaKey = type.schema.properties?.get(attrId)?.key
                    ?: error("Attribute $attrId not found in schema for entity type ${type.key}")
                attrId to EntityAttributePrimitivePayload(value = raw, schemaType = schemaKey)
            }
            .toMap()

        val enrichedPayload = injectDefaultsAndGenerateIds(
            attributePayload = primitivePayload,
            schema = type.schema,
            entityTypeId = typeId,
            isCreate = prev == null,
            previousAttributes = previousAttributes,
        )

        val entity = if (prev != null) {
            prev.copy(
                sourceType = sourceType,
                sourceIntegrationId = sourceIntegrationId,
                sourceExternalId = sourceExternalId,
            )
        } else {
            EntityEntity(
                workspaceId = workspaceId,
                typeId = entityTypeId,
                typeKey = type.key,
                identifierKey = type.identifierKey,
                iconType = type.iconType,
                iconColour = type.iconColour,
                sourceType = sourceType,
                sourceIntegrationId = sourceIntegrationId,
                sourceExternalId = sourceExternalId,
            )
        }

        // System note: `readonly` is currently a property of the legacy NoteEntity table.
        // The graduated entities table has no `readonly` column — provenance via sourceType
        // (e.g. INTEGRATION) is the intended marker. Phase F may revisit this.
        @Suppress("UNUSED_VARIABLE")
        val readonlyHint = readonly

        entityValidationService.validateEntity(
            entity,
            type,
            attributes = enrichedPayload,
            isUpdate = prev != null,
            previousAttributes = previousAttributes,
        ).run {
            if (isNotEmpty()) {
                throw SchemaValidationException(this)
            }
        }

        val saved = entityRepository.save(entity)
        val savedId = requireNotNull(saved.id) { "Saved entity ID cannot be null" }

        entityAttributeService.saveAttributes(
            entityId = savedId,
            workspaceId = workspaceId,
            typeId = typeId,
            attributes = enrichedPayload,
        )

        // Unique-value enforcement (mirrors saveEntity).
        val uniqueValuesToSave = enrichedPayload
            .filterValues { it.value != null }
            .mapNotNull { (fieldId, payload) ->
                val schemaProp = type.schema.properties?.get(fieldId) ?: return@mapNotNull null
                if (!schemaProp.unique) return@mapNotNull null
                entityTypeAttributeService.checkAttributeUniqueness(
                    typeId = typeId,
                    fieldId = fieldId,
                    value = payload.value,
                    excludeEntityId = savedId,
                )
                fieldId to payload.value.toString()
            }
            .toMap()

        entityTypeAttributeService.saveUniqueValues(
            workspaceId = workspaceId,
            entityId = savedId,
            typeId = typeId,
            uniqueValues = uniqueValuesToSave,
        )

        return saved
    }

    /**
     * System-driven soft delete. Mirrors the cascade in [deleteEntities] for a single
     * entity ID, without the JWT-based activity log or impact-analysis machinery.
     */
    @Transactional
    fun softDeleteEntityInternal(workspaceId: UUID, entityId: UUID) {
        executeCascadeDelete(listOf(entityId), workspaceId)
    }

    /**
     * System-driven relationship replace. Drives all rows for `(sourceEntityId, relationshipDefinitionId)`
     * toward the desired [targetEntityIds] set. Used by the knowledge ingestion layer to
     * reconcile system relationships (`ATTACHMENT`, `MENTION`, `DEFINES`) on every upsert.
     *
     * `targetKind` selects ENTITY (default), ENTITY_TYPE or ATTRIBUTE rows; reconciliation
     * only sweeps existing rows whose kind matches the supplied value, so glossary DEFINES
     * batches at different kinds on the same definition row do not clobber each other.
     */
    @Transactional
    fun replaceRelationshipsInternal(
        workspaceId: UUID,
        sourceEntityId: UUID,
        relationshipDefinitionId: UUID,
        targetEntityIds: Set<UUID>,
        linkSource: SourceType,
        targetKind: RelationshipTargetKind = RelationshipTargetKind.ENTITY,
    ) {
        entityRelationshipService.replaceForDefinition(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            definitionId = relationshipDefinitionId,
            targetEntityIds = targetEntityIds,
            linkSource = linkSource,
            targetKind = targetKind,
        )
    }

    /**
     * System-driven find-by-id. Skips workspace-bound `@PreAuthorize` so it can be invoked
     * from background contexts; returns null when the row is missing rather than throwing.
     */
    fun findByIdInternal(workspaceId: UUID, entityId: UUID): EntityEntity? =
        entityRepository.findByIdAndWorkspaceId(entityId, workspaceId).orElse(null)

    /**
     * System-driven listing by entity-type key within a workspace. Used by the knowledge
     * projector layer to read graduated knowledge entities (note, glossary) without a
     * relationship-definition lookup.
     */
    fun findByTypeKeyInternal(workspaceId: UUID, typeKey: String): List<EntityEntity> =
        entityRepository.findByWorkspaceIdAndTypeKey(workspaceId, typeKey)

    /**
     * Enriches the attribute payload with default values from the schema and auto-generated IDs.
     * On create: generates IDs for ID-type attributes and injects defaults for missing attributes.
     * On update: carries forward existing ID values from the database when not in payload.
     */
    private fun injectDefaultsAndGenerateIds(
        attributePayload: Map<UUID, EntityAttributePrimitivePayload>,
        schema: Schema<UUID>,
        entityTypeId: UUID,
        isCreate: Boolean,
        previousAttributes: Map<UUID, EntityAttributePrimitivePayload> = emptyMap(),
    ): Map<UUID, EntityAttributePrimitivePayload> {
        val enriched = attributePayload.toMutableMap()

        schema.properties?.forEach { (attrId, attrSchema) ->
            if (attrSchema.key == SchemaType.ID) {
                if (isCreate && !enriched.containsKey(attrId)) {
                    // Generate new sequential ID on create
                    val prefix = requireNotNull(attrSchema.options?.prefix) {
                        "ID attribute '$attrId' must have a prefix configured in options"
                    }
                    val nextVal = sequenceService.nextValue(entityTypeId, attrId)
                    enriched[attrId] = EntityAttributePrimitivePayload(
                        value = sequenceService.formatId(prefix, nextVal),
                        schemaType = SchemaType.ID,
                    )
                } else if (!isCreate && !enriched.containsKey(attrId)) {
                    // Carry forward existing ID on update
                    previousAttributes[attrId]?.let { enriched[attrId] = it }
                }
            } else if (isCreate && !enriched.containsKey(attrId)) {
                // Inject default value for attributes not provided on create
                resolveDefault(attrSchema)?.let { resolved ->
                    enriched[attrId] = EntityAttributePrimitivePayload(
                        value = resolved,
                        schemaType = attrSchema.key,
                    )
                }
            }
        }

        return enriched
    }

    /**
     * Resolves the effective default value for an attribute schema.
     *
     * @return The resolved default value, or null if no default is configured.
     */
    private fun resolveDefault(attrSchema: Schema<UUID>): Any? {
        val dv = attrSchema.options?.defaultValue ?: return null
        return when (dv) {
            is DefaultValue.Static -> dv.value
            is DefaultValue.Dynamic -> resolveDynamicFunction(dv.function)
        }
    }

    private fun resolveDynamicFunction(function: DynamicDefaultFunction): String =
        when (function) {
            DynamicDefaultFunction.CURRENT_DATE -> LocalDate.now().toString()
            DynamicDefaultFunction.CURRENT_DATETIME -> OffsetDateTime.now().toString()
        }

    /**
     * Publishes an IdentityMatchTriggerEvent for the saved entity.
     *
     * Filters both old and new attribute maps to only include IDENTIFIER-classified attributes.
     * The event is consumed by IdentityMatchTriggerListener after transaction commit.
     */
    private fun publishIdentityMatchTriggerEvent(
        entityId: UUID,
        workspaceId: UUID,
        typeId: UUID,
        isUpdate: Boolean,
        previousAttributes: Map<UUID, EntityAttributePrimitivePayload>,
        enrichedPayload: Map<UUID, EntityAttributePrimitivePayload>,
    ) {
        val identifierAttrIds = entityTypeClassificationService.getIdentifierAttributeIds(typeId)
        val prevIdentifiers = previousAttributes
            .filterKeys { it in identifierAttrIds }
            .mapValues { it.value.value }
        val newIdentifiers = enrichedPayload
            .filterKeys { it in identifierAttrIds }
            .mapValues { it.value.value }

        if (prevIdentifiers.isEmpty() && newIdentifiers.isEmpty()) return
        if (prevIdentifiers == newIdentifiers) return

        applicationEventPublisher.publishEvent(
            IdentityMatchTriggerEvent(
                entityId = entityId,
                workspaceId = workspaceId,
                entityTypeId = typeId,
                isUpdate = isUpdate,
                previousIdentifierAttributes = prevIdentifiers,
                newIdentifierAttributes = newIdentifiers,
            )
        )
    }

    /**
     * Saves relationships for each definition in the payload.
     *
     * Loads relationship definitions for the entity type, then calls
     * EntityRelationshipService.saveRelationships for each definition.
     */
    private fun saveRelationshipsPerDefinition(
        entityId: UUID,
        workspaceId: UUID,
        entityTypeId: UUID,
        relationshipPayload: Map<UUID, List<UUID>>,
    ) {
        if (relationshipPayload.isEmpty()) return

        val definitions = entityTypeRelationshipService.getDefinitionsForEntityType(workspaceId, entityTypeId)
        val definitionsById = definitions.associateBy { it.id }

        relationshipPayload.forEach { (definitionId, targetIds) ->
            val definition = definitionsById[definitionId]
                ?: throw IllegalArgumentException("Unknown relationship definition: $definitionId")
            require(definition.sourceEntityTypeId == entityTypeId) {
                "Definition $definitionId belongs to source type ${definition.sourceEntityTypeId}, not $entityTypeId. " +
                    "Inverse definitions cannot be used to create relationships."
            }
            entityRelationshipService.saveRelationships(
                id = entityId,
                workspaceId = workspaceId,
                definitionId = definitionId,
                definition = definition,
                targetIds = targetIds,
            )
        }
    }


    /**
     * Executes the core soft-delete cascade for a batch of entity IDs:
     * soft-deletes entities, their unique constraint values, attributes, and relationships.
     *
     * @return the list of EntityEntity rows that were actually soft-deleted by the database
     */
    private fun executeCascadeDelete(ids: Collection<UUID>, workspaceId: UUID): List<EntityEntity> {
        val deletedEntities = entityRepository.deleteByIds(ids.toTypedArray(), workspaceId)
        val deletedRowIds = deletedEntities.mapNotNull { it.id }.toSet()

        if (deletedRowIds.isNotEmpty()) {
            entityTypeAttributeService.deleteEntities(workspaceId, deletedRowIds)
            entityAttributeService.softDeleteByEntityIds(workspaceId, deletedRowIds)
            entityRelationshipService.archiveEntities(deletedRowIds, workspaceId)
        }

        return deletedEntities
    }

    /**
     * Publishes EntityEvent for each entity type group in the deleted entities.
     */
    private fun publishDeleteEvents(deletedEntities: List<EntityEntity>, workspaceId: UUID, userId: UUID) {
        deletedEntities
            .groupBy { it.typeId to it.typeKey }
            .forEach { (typeInfo, entities) ->
                val (deletedTypeId, deletedTypeKey) = typeInfo
                applicationEventPublisher.publishEvent(
                    EntityEvent(
                        workspaceId = workspaceId,
                        userId = userId,
                        operation = OperationType.DELETE,
                        entityId = null,
                        entityTypeId = deletedTypeId,
                        entityTypeKey = deletedTypeKey,
                        summary = mapOf(
                            "deletedIds" to entities.mapNotNull { it.id },
                            "deletedCount" to entities.size,
                            "userDisplayName" to authTokenService.getUserDisplayName(),
                        ),
                    )
                )
            }
    }

    /**
     * Fetches entities that lost relationships due to deletions, grouped by type ID.
     * Returns null if no entities were impacted.
     */
    private fun fetchImpactedEntities(impactedEntityIds: List<UUID>, workspaceId: UUID): Map<UUID, List<Entity>>? {
        if (impactedEntityIds.isEmpty()) return null

        // Run on the current transaction thread to preserve @Transactional context
        val impactedEntityEntities = entityRepository.findByIdInAndWorkspaceId(impactedEntityIds, workspaceId)
        val impactedRelationships = entityRelationshipService.findRelatedEntities(
            entityIds = impactedEntityIds.toSet(),
            workspaceId = workspaceId,
        )
        val impactedAttributes = entityAttributeService.getAttributesForEntities(impactedEntityIds.toSet())

        return impactedEntityEntities
            .map { impactedEntity ->
                val impactedId = requireNotNull(impactedEntity.id) { "Impacted entity ID must not be null" }
                impactedEntity.toModel(
                    audit = true,
                    relationships = impactedRelationships[impactedId] ?: emptyMap(),
                    attributes = impactedAttributes[impactedId] ?: emptyMap(),
                )
            }
            .groupBy { it.typeId }
    }


    // ------ Bulk Delete ------

    /**
     * Bulk deletes entities by explicit IDs or filter-based selection.
     * Processes deletes in batches of 500. Logs one activity entry per bulk operation.
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteEntities(workspaceId: UUID, request: DeleteEntityRequest): DeleteEntityResponse {
        val userId = authTokenService.getUserId()

        val idsToDelete = resolveEntityIds(request, workspaceId)
        if (idsToDelete.isEmpty()) {
            throw NotFoundException("No entities matched the selection criteria")
        }

        // Detect impacted entities before any deletes
        val impactedEntityIds = entityRelationshipService.findByTargetIdIn(idsToDelete)
            .flatMap { it.value }
            .map { it.sourceId }
            .toSet()
            .filter { it !in idsToDelete }

        // Batch cascade delete
        val allDeletedEntities = idsToDelete.chunked(BULK_DELETE_BATCH_SIZE).flatMap { batch ->
            executeCascadeDelete(batch, workspaceId)
        }
        val deletedCount = allDeletedEntities.mapNotNull { it.id }.toSet().size

        if (deletedCount == 0) {
            throw NotFoundException("No entities were deleted. Please check the selection criteria.")
        }

        // Log one bulk activity entry
        activityService.logActivities(
            listOf(
                ActivityLogEntity(
                    activity = Activity.ENTITY,
                    operation = OperationType.DELETE,
                    userId = userId,
                    workspaceId = workspaceId,
                    entityId = null,
                    entityType = ApplicationEntityType.ENTITY,
                    details = mapOf(
                        "deletedCount" to deletedCount,
                        "deletedIds" to allDeletedEntities.mapNotNull { it.id },
                        "selectionType" to request.type.name,
                    )
                )
            )
        )

        publishDeleteEvents(allDeletedEntities, workspaceId, userId)

        val updatedEntities = fetchImpactedEntities(impactedEntityIds, workspaceId)

        return DeleteEntityResponse(
            deletedCount = deletedCount,
            updatedEntities = updatedEntities
        )
    }

    /**
     * Resolves entity IDs to delete based on the request type.
     * For BY_ID: returns entityIds directly.
     * For ALL: queries via EntityQueryService with pagination, removes excludeIds.
     */
    private fun resolveEntityIds(request: DeleteEntityRequest, workspaceId: UUID): List<UUID> {
        return when (request.type) {
            EntitySelectType.BY_ID -> requireNotNull(request.entityIds) { "entityIds required for BY_ID" }

            EntitySelectType.ALL -> {
                val entityTypeId = requireNotNull(request.entityTypeId) { "entityTypeId required for ALL" }
                val query = EntityQuery(entityTypeId = entityTypeId, filter = request.filter)
                val excludeIds = request.excludeIds?.toSet() ?: emptySet()

                val allIds = mutableListOf<UUID>()
                var offset = 0

                do {
                    val result = runBlocking {
                        entityQueryService.execute(
                            query = query,
                            workspaceId = workspaceId,
                            pagination = QueryPagination(limit = BULK_DELETE_BATCH_SIZE, offset = offset),
                            includeCount = false,
                        )
                    }
                    allIds.addAll(result.entities.map { it.id })
                    offset += BULK_DELETE_BATCH_SIZE
                } while (result.hasNextPage)

                if (excludeIds.isNotEmpty()) {
                    allIds.filter { it !in excludeIds }
                } else {
                    allIds
                }
            }
        }
    }

    /**
     * Get all entities for an workspace.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getWorkspaceEntities(workspaceId: UUID): List<Entity> {
        val entities = findManyResults { entityRepository.findByWorkspaceId(workspaceId) }
        val entityIds = entities.mapNotNull { it.id }.toSet()
        val allAttributes = entityAttributeService.getAttributesForEntities(entityIds)
        return entities.map {
            val id = requireNotNull(it.id)
            it.toModel(
                relationships = emptyMap(),
                attributes = allAttributes[id] ?: emptyMap(),
            )
        }
    }

    companion object {
        const val BULK_DELETE_BATCH_SIZE = 500
    }
}
