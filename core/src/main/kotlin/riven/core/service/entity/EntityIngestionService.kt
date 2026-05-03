package riven.core.service.entity

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DynamicDefaultFunction
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.integration.SourceType
import riven.core.exceptions.SchemaValidationException
import riven.core.models.common.validation.DefaultValue
import riven.core.models.common.validation.Schema
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.entity.EntityRepository
import riven.core.service.entity.type.EntityTypeAttributeService
import riven.core.service.entity.type.EntityTypeSequenceService
import riven.core.service.entity.type.EntityTypeService
import riven.core.util.ServiceUtil.findOrThrow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * System-driven entity persistence — no JWT, no per-call workspace authority check.
 *
 * Entry points here are intentionally callable from background contexts that do not have a
 * `JwtAuthenticationToken` on the security context, including:
 *   - Temporal activities (e.g. note / glossary backfill workflows);
 *   - the knowledge ingestion layer ([riven.core.service.knowledge.AbstractKnowledgeEntityIngestionService])
 *     and its subclasses;
 *   - read paths from projectors that need a workspace-scoped lookup without a `@PreAuthorize`
 *     re-entry.
 *
 * **Never inject this service into JWT-fronted controllers.** Controllers must go through
 * [EntityService] (annotated with `@PreAuthorize` / `@PostAuthorize`) so workspace authority
 * checks run on the caller's token. This split — JWT path on [EntityService], system bus on
 * [EntityIngestionService] — is the architectural boundary referenced by CLAUDE.md when
 * splitting services by responsibility.
 *
 * Callers own activity logging and identity-match event emission when those apply (the
 * ingestion services log via `ActivityService` themselves; backfill workflows record their
 * own outcome counters).
 */
@Service
class EntityIngestionService(
    private val entityRepository: EntityRepository,
    private val entityTypeService: EntityTypeService,
    private val entityRelationshipService: EntityRelationshipService,
    private val entityValidationService: EntityValidationService,
    private val entityTypeAttributeService: EntityTypeAttributeService,
    private val entityAttributeService: EntityAttributeService,
    private val sequenceService: EntityTypeSequenceService,
    private val logger: KLogger,
) {

    // ------ Public mutations ------

    /**
     * System-driven entity upsert. Bypasses [PreAuthorize] and JWT-based user lookup.
     *
     * Differences from [EntityService.saveEntity]:
     *   - accepts a flat `Map<UUID, Any?>` payload pre-resolved to attribute UUIDs;
     *   - supports upsert via [existingId] (looked up under the workspace);
     *   - threads provenance fields ([sourceType], [sourceIntegrationId], [sourceExternalId])
     *     onto the persisted [EntityEntity];
     *   - skips identity-match event emission and activity logging — the caller owns those.
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
    ): EntityEntity {
        val (type, typeId) = resolveAndAuthorizeType(workspaceId, entityTypeId)
        val prev = loadPreviousEntity(existingId, workspaceId, entityTypeId)
        val previousAttributes = prev?.let { entityAttributeService.getAttributes(requireNotNull(it.id)) } ?: emptyMap()

        val primitivePayload = wrapPayload(type, attributePayload)
        val enrichedPayload = injectDefaultsAndGenerateIds(
            attributePayload = primitivePayload,
            schema = type.schema,
            entityTypeId = typeId,
            isCreate = prev == null,
            previousAttributes = previousAttributes,
        )

        val entity = buildEntity(prev, type, workspaceId, entityTypeId, sourceType, sourceIntegrationId, sourceExternalId)

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

        enforceUniqueValues(type, savedId, workspaceId, typeId, enrichedPayload)

        logger.debug { "saveEntityInternal saved entity $savedId in workspace $workspaceId (type=${type.key})" }
        return saved
    }

    /**
     * System-driven soft delete. Mirrors the cascade in [EntityService.deleteEntities] for a
     * single entity ID, without the JWT-based activity log or impact-analysis machinery.
     */
    @Transactional
    fun softDeleteEntityInternal(workspaceId: UUID, entityId: UUID) {
        val deletedEntities = entityRepository.deleteByIds(arrayOf(entityId), workspaceId)
        val deletedRowIds = deletedEntities.mapNotNull { it.id }.toSet()
        if (deletedRowIds.isEmpty()) return

        entityTypeAttributeService.deleteEntities(workspaceId, deletedRowIds)
        entityAttributeService.softDeleteByEntityIds(workspaceId, deletedRowIds)
        entityRelationshipService.archiveEntities(deletedRowIds, workspaceId)
    }

    /**
     * System-driven relationship replace. Drives all rows for `(sourceEntityId, relationshipDefinitionId, targetKind)`
     * toward the desired [targetIds] set. Used by the knowledge ingestion layer to reconcile
     * system relationships (`ATTACHMENT`, `MENTION`, `DEFINES`) on every upsert.
     *
     * `targetKind` selects ENTITY (default), ENTITY_TYPE, ATTRIBUTE, or RELATIONSHIP rows;
     * reconciliation only sweeps existing rows whose kind matches the supplied value, so
     * glossary DEFINES batches at different kinds on the same definition row do not clobber
     * each other.
     *
     * For sub-reference target_kinds (ATTRIBUTE, RELATIONSHIP), [targetParentId] is the owning
     * entity_type id; required by the entity_relationships CHECK constraint. NULL for ENTITY /
     * ENTITY_TYPE.
     */
    @Transactional
    fun replaceRelationshipsInternal(
        workspaceId: UUID,
        sourceEntityId: UUID,
        relationshipDefinitionId: UUID,
        targetIds: Set<UUID>,
        linkSource: SourceType,
        targetKind: RelationshipTargetKind = RelationshipTargetKind.ENTITY,
        targetParentId: UUID? = null,
    ) {
        entityRelationshipService.replaceForDefinitionInternal(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            definitionId = relationshipDefinitionId,
            targetIds = targetIds,
            linkSource = linkSource,
            targetKind = targetKind,
            targetParentId = targetParentId,
        )
    }

    /**
     * System-driven clear-all-of-kind reconciliation. Used by knowledge ingestion when the
     * input carries no refs for a parent-scoped kind (ATTRIBUTE / RELATIONSHIP) — in that case
     * [replaceRelationshipsInternal] cannot run because it requires a non-null `targetParentId`,
     * so callers route through this method to drop every existing row of the kind regardless
     * of parent.
     */
    @Transactional
    fun clearRelationshipsByKindInternal(
        workspaceId: UUID,
        sourceEntityId: UUID,
        relationshipDefinitionId: UUID,
        targetKind: RelationshipTargetKind,
    ) {
        entityRelationshipService.clearAllOfKindForDefinition(
            workspaceId = workspaceId,
            sourceId = sourceEntityId,
            definitionId = relationshipDefinitionId,
            targetKind = targetKind,
        )
    }

    // ------ Public reads ------

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

    // ------ Private helpers ------

    /**
     * Resolves the entity type and asserts it belongs to the requested workspace. Closes the
     * cross-tenant attachment gap on this system-bus entry point — without this guard a caller
     * could attach a foreign-workspace entity type to a workspace entity.
     */
    private fun resolveAndAuthorizeType(workspaceId: UUID, entityTypeId: UUID): Pair<EntityTypeEntity, UUID> {
        val type = entityTypeService.getById(entityTypeId)
        require(type.workspaceId == workspaceId) { "Entity type $entityTypeId not found in workspace $workspaceId" }
        val typeId = requireNotNull(type.id) { "Entity type ID cannot be null" }
        return type to typeId
    }

    private fun loadPreviousEntity(existingId: UUID?, workspaceId: UUID, entityTypeId: UUID): EntityEntity? {
        if (existingId == null) return null
        val prev = findOrThrow { entityRepository.findById(existingId) }
        require(prev.workspaceId == workspaceId) { "Entity does not belong to the specified workspace" }
        require(prev.typeId == entityTypeId) { "Entity type cannot be changed" }
        return prev
    }

    /**
     * Wraps raw attribute values into [EntityAttributePrimitivePayload] using each attribute's
     * declared schemaType. Skips null values.
     */
    private fun wrapPayload(
        type: EntityTypeEntity,
        attributePayload: Map<UUID, Any?>,
    ): Map<UUID, EntityAttributePrimitivePayload> = attributePayload
        .mapNotNull { (attrId, raw) ->
            if (raw == null) return@mapNotNull null
            val schemaKey = type.schema.properties?.get(attrId)?.key
                ?: throw SchemaValidationException(
                    listOf("Attribute $attrId not found in schema for entity type ${type.key}")
                )
            attrId to EntityAttributePrimitivePayload(value = raw, schemaType = schemaKey)
        }
        .toMap()

    /**
     * Builds the [EntityEntity] for upsert. Update path copies provenance fields onto the
     * existing row; create path constructs a fresh entity stamped with the type's identifiers.
     *
     * Readonly state is derived from sourceType (specifically [SourceType.INTEGRATION]) rather
     * than stored as a separate column — single source of truth checked by NoteEntityProjector,
     * NoteService, and GlossaryEntityProjector when deciding whether mutations are allowed.
     */
    private fun buildEntity(
        prev: EntityEntity?,
        type: EntityTypeEntity,
        workspaceId: UUID,
        entityTypeId: UUID,
        sourceType: SourceType,
        sourceIntegrationId: UUID?,
        sourceExternalId: String?,
    ): EntityEntity = prev?.copy(
        sourceType = sourceType,
        sourceIntegrationId = sourceIntegrationId,
        sourceExternalId = sourceExternalId,
    ) ?: EntityEntity(
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

    /**
     * Enforces unique-value constraints on attributes flagged unique in the schema. Mirrors
     * the equivalent step in [EntityService.saveEntity].
     */
    private fun enforceUniqueValues(
        type: EntityTypeEntity,
        savedId: UUID,
        workspaceId: UUID,
        typeId: UUID,
        enrichedPayload: Map<UUID, EntityAttributePrimitivePayload>,
    ) {
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
    }

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
                    val prefix = requireNotNull(attrSchema.options?.prefix) {
                        "ID attribute '$attrId' must have a prefix configured in options"
                    }
                    val nextVal = sequenceService.nextValue(entityTypeId, attrId)
                    enriched[attrId] = EntityAttributePrimitivePayload(
                        value = sequenceService.formatId(prefix, nextVal),
                        schemaType = SchemaType.ID,
                    )
                } else if (!isCreate && !enriched.containsKey(attrId)) {
                    previousAttributes[attrId]?.let { enriched[attrId] = it }
                }
            } else if (isCreate && !enriched.containsKey(attrId)) {
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
}
