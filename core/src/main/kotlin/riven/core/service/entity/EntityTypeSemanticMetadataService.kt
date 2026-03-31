package riven.core.service.entity

import io.github.oshai.kotlinlogging.KLogger
import jakarta.transaction.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.EntityTypeSemanticMetadataEntity
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.entity.semantics.SemanticMetadataTargetType.ATTRIBUTE
import riven.core.enums.entity.semantics.SemanticMetadataTargetType.ENTITY_TYPE
import riven.core.enums.entity.semantics.SemanticMetadataTargetType.RELATIONSHIP
import riven.core.enums.identity.MatchSignalType
import riven.core.models.entity.EntityTypeSemanticMetadata
import riven.core.models.request.entity.type.BulkSaveSemanticMetadataRequest
import riven.core.models.request.entity.type.SaveSemanticMetadataRequest
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.service.identity.EntityTypeClassificationService
import riven.core.util.ServiceUtil
import java.util.UUID

/**
 * Service for managing semantic metadata attached to entity types, their attributes,
 * and their relationship definitions.
 *
 * Metadata records are created automatically when entity types are published or when
 * attributes/relationships are added. This service provides CRUD operations on those records
 * and exposes lifecycle hooks called by EntityTypeService, EntityTypeAttributeService, and
 * EntityTypeRelationshipService.
 *
 * No activity logging is performed for metadata mutations (locked decision).
 */
@Service
class EntityTypeSemanticMetadataService(
    private val repository: EntityTypeSemanticMetadataRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val classificationService: EntityTypeClassificationService,
    private val logger: KLogger,
) {

    // ------ Public read operations ------

    /**
     * Get the semantic metadata record for an entity type itself (not its attributes/relationships).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getForEntityType(workspaceId: UUID, entityTypeId: UUID): EntityTypeSemanticMetadata {
        verifyEntityTypeBelongsToWorkspace(workspaceId, entityTypeId)
        return ServiceUtil.findOrThrow {
            repository.findByEntityTypeIdAndTargetTypeAndTargetId(entityTypeId, ENTITY_TYPE, entityTypeId)
        }.toModel()
    }

    /**
     * Get all attribute-level semantic metadata records for an entity type.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getAttributeMetadata(workspaceId: UUID, entityTypeId: UUID): List<EntityTypeSemanticMetadata> {
        verifyEntityTypeBelongsToWorkspace(workspaceId, entityTypeId)
        return repository.findByEntityTypeIdAndTargetType(entityTypeId, ATTRIBUTE).map { it.toModel() }
    }

    /**
     * Get all relationship-level semantic metadata records for an entity type.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getRelationshipMetadata(workspaceId: UUID, entityTypeId: UUID): List<EntityTypeSemanticMetadata> {
        verifyEntityTypeBelongsToWorkspace(workspaceId, entityTypeId)
        return repository.findByEntityTypeIdAndTargetType(entityTypeId, RELATIONSHIP).map { it.toModel() }
    }

    /**
     * Get all semantic metadata for an entity type (entity type itself + attributes + relationships).
     *
     * Used by the `?include=semantics` feature on a single entity type fetch.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getAllMetadataForEntityType(workspaceId: UUID, entityTypeId: UUID): List<EntityTypeSemanticMetadata> {
        verifyEntityTypeBelongsToWorkspace(workspaceId, entityTypeId)
        return repository.findByEntityTypeId(entityTypeId).map { it.toModel() }
    }

    /**
     * Get all semantic metadata records for a collection of entity type IDs.
     *
     * No @PreAuthorize — called internally from controllers that already hold workspace context.
     * Used for batch `?include=semantics` on workspace entity type list queries.
     */
    fun getMetadataForEntityTypes(entityTypeIds: List<UUID>): List<EntityTypeSemanticMetadata> {
        return repository.findByEntityTypeIdIn(entityTypeIds).map { it.toModel() }
    }

    // ------ Public mutations ------

    /**
     * Upsert semantic metadata for a specific target within an entity type.
     *
     * PUT semantics: all fields (definition, classification, tags) are fully replaced on every call.
     * No activity logging per locked decision.
     *
     * @param workspaceId workspace owning the entity type
     * @param entityTypeId the entity type this metadata belongs to
     * @param targetType discriminates whether the target is the entity type, an attribute, or a relationship
     * @param targetId the UUID of the specific target (attribute UUID, relationship UUID, or entityTypeId for ENTITY_TYPE)
     * @param request the new metadata values
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun upsertMetadata(
        workspaceId: UUID,
        entityTypeId: UUID,
        targetType: SemanticMetadataTargetType,
        targetId: UUID,
        request: SaveSemanticMetadataRequest,
    ): EntityTypeSemanticMetadata {
        verifyEntityTypeBelongsToWorkspace(workspaceId, entityTypeId)
        return upsertMetadataInternal(workspaceId, entityTypeId, targetType, targetId, request)
    }

    /**
     * Upsert semantic metadata from within an already-secured transactional context.
     *
     * No @PreAuthorize — the caller's service method already holds workspace authorization.
     * Follows the same pattern as [initializeForTarget].
     */

    /**
     * Bulk upsert attribute-level semantic metadata for an entity type.
     *
     * Fetches all existing attribute metadata in a single query to avoid N+1 lookups,
     * then updates or creates records as needed and saves in one batch.
     *
     * @param workspaceId workspace owning the entity type
     * @param entityTypeId the entity type whose attribute metadata is being updated
     * @param requests list of per-attribute metadata updates
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun bulkUpsertAttributeMetadata(
        workspaceId: UUID,
        entityTypeId: UUID,
        requests: List<BulkSaveSemanticMetadataRequest>,
    ): List<EntityTypeSemanticMetadata> {
        verifyEntityTypeBelongsToWorkspace(workspaceId, entityTypeId)

        val existingByTargetId = repository.findByEntityTypeIdAndTargetType(entityTypeId, ATTRIBUTE)
            .associateBy { it.targetId }

        val entitiesToSave = requests.map { req ->
            val existing = existingByTargetId[req.targetId]
            if (existing != null) {
                existing.apply {
                    definition = req.definition
                    classification = req.classification
                    signalType = req.signalType ?: existing.signalType ?: deriveSignalType(req.classification)
                    tags = req.tags
                }
            } else {
                EntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetType = ATTRIBUTE,
                    targetId = req.targetId,
                    definition = req.definition,
                    classification = req.classification,
                    signalType = req.signalType ?: deriveSignalType(req.classification),
                    tags = req.tags,
                )
            }
        }

        val saved = repository.saveAll(entitiesToSave).map { it.toModel() }
        classificationService.invalidate(entityTypeId)
        return saved
    }

    // ------ Lifecycle hooks (called from other services) ------

    /**
     * Initialize semantic metadata records when an entity type is first published.
     *
     * Creates one ENTITY_TYPE record for the entity type itself plus one ATTRIBUTE record
     * for each attribute ID. All records start empty (no definition, classification, or tags).
     *
     * Called within an existing @Transactional/@PreAuthorize context from EntityTypeService.
     *
     * @param entityTypeId the newly published entity type
     * @param workspaceId workspace owning the entity type
     * @param attributeIds IDs of the initial attributes (typically just the identifier attribute)
     */
    fun initializeForEntityType(entityTypeId: UUID, workspaceId: UUID, attributeIds: List<UUID>) {
        val records = mutableListOf<EntityTypeSemanticMetadataEntity>()

        records.add(
            EntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = ENTITY_TYPE,
                targetId = entityTypeId,
            )
        )

        attributeIds.forEach { attributeId ->
            records.add(
                EntityTypeSemanticMetadataEntity(
                    workspaceId = workspaceId,
                    entityTypeId = entityTypeId,
                    targetType = ATTRIBUTE,
                    targetId = attributeId,
                )
            )
        }

        repository.saveAll(records)
        logger.debug { "Initialized semantic metadata for entity type $entityTypeId with ${attributeIds.size} attributes" }
    }

    /**
     * Initialize a single semantic metadata record for a new attribute or relationship
     * added to an entity type after its initial creation.
     *
     * @param entityTypeId the owning entity type
     * @param workspaceId workspace owning the entity type
     * @param targetType ATTRIBUTE or RELATIONSHIP
     * @param targetId UUID of the new attribute or relationship
     */
    fun initializeForTarget(
        entityTypeId: UUID,
        workspaceId: UUID,
        targetType: SemanticMetadataTargetType,
        targetId: UUID,
    ) {
        repository.save(
            EntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = targetType,
                targetId = targetId,
            )
        )
    }

    /**
     * Hard-delete the semantic metadata record for a specific attribute or relationship.
     *
     * Called when an attribute or relationship is removed from an entity type schema.
     * Per locked decision, metadata for removed attributes/relationships is physically deleted.
     *
     * @param entityTypeId the owning entity type
     * @param targetType ATTRIBUTE or RELATIONSHIP
     * @param targetId UUID of the removed attribute or relationship
     */
    fun deleteForTarget(entityTypeId: UUID, targetType: SemanticMetadataTargetType, targetId: UUID) {
        repository.hardDeleteByTarget(entityTypeId, targetType, targetId)
    }

    /**
     * Soft-delete all semantic metadata records belonging to an entity type.
     *
     * Called when the entity type itself is soft-deleted. Records are hidden from live
     * queries via the @SQLRestriction filter but preserved for audit purposes.
     *
     * @param entityTypeId the entity type being soft-deleted
     */
    fun softDeleteForEntityType(entityTypeId: UUID) {
        repository.softDeleteByEntityTypeId(entityTypeId)
    }

    /**
     * Placeholder for future restore functionality.
     *
     * Cannot be implemented with standard derived queries because the @SQLRestriction
     * filter (deleted = false) hides soft-deleted rows from all JPQL operations.
     * Requires a native query that bypasses the filter — tracked as a future task.
     *
     * @param entityTypeId the entity type to restore metadata for
     */
    fun restoreForEntityType(entityTypeId: UUID) {
        throw UnsupportedOperationException(
            "restoreForEntityType not yet implemented — requires native query to bypass @SQLRestriction"
        )
    }

    // ------ Private helpers ------

    fun upsertMetadataInternal(
        workspaceId: UUID,
        entityTypeId: UUID,
        targetType: SemanticMetadataTargetType,
        targetId: UUID,
        request: SaveSemanticMetadataRequest,
    ): EntityTypeSemanticMetadata {
        val existing = repository.findByEntityTypeIdAndTargetTypeAndTargetId(entityTypeId, targetType, targetId)

        val entity = if (existing.isPresent) {
            existing.get().apply {
                definition = request.definition
                classification = request.classification
                signalType = request.signalType ?: existing.get().signalType ?: deriveSignalType(request.classification)
                tags = request.tags
            }
        } else {
            EntityTypeSemanticMetadataEntity(
                workspaceId = workspaceId,
                entityTypeId = entityTypeId,
                targetType = targetType,
                targetId = targetId,
                definition = request.definition,
                classification = request.classification,
                signalType = request.signalType ?: deriveSignalType(request.classification),
                tags = request.tags,
            )
        }

        val saved = repository.save(entity).toModel()
        classificationService.invalidate(entityTypeId)
        return saved
    }

    /**
     * Derives the [MatchSignalType] from the classification.
     *
     * Returns [MatchSignalType.CUSTOM_IDENTIFIER] when classification is IDENTIFIER
     * (the JPA converter handles the CUSTOM_IDENTIFIER <-> "CUSTOM" DB mapping).
     * Returns null when classification is not IDENTIFIER, clearing any previously stored signal type.
     */
    private fun deriveSignalType(
        classification: SemanticAttributeClassification?,
    ): MatchSignalType? = when (classification) {
        SemanticAttributeClassification.IDENTIFIER -> MatchSignalType.CUSTOM_IDENTIFIER
        else -> null
    }

    /**
     * Fetch the entity type and assert it belongs to the given workspace.
     *
     * Prevents operations where a caller supplies an entityTypeId from a different workspace.
     *
     * @return the entity type entity (callers may use it to avoid a second lookup)
     * @throws NotFoundException if the entity type does not exist
     * @throws IllegalArgumentException if the entity type belongs to a different workspace
     */
    private fun verifyEntityTypeBelongsToWorkspace(workspaceId: UUID, entityTypeId: UUID): EntityTypeEntity {
        val entityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(entityTypeId) }
        require(entityType.workspaceId == workspaceId) {
            "Entity type $entityTypeId does not belong to workspace $workspaceId"
        }
        return entityType
    }
}
