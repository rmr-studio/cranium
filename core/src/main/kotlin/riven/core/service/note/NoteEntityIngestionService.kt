package riven.core.service.note

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.enums.knowledge.KnowledgeEntityTypeKey
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.EntityIngestionService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.knowledge.AbstractKnowledgeEntityIngestionService
import riven.core.service.knowledge.KnowledgeIngestionInput
import riven.core.service.knowledge.KnowledgeRelationshipBatch
import java.util.UUID

/**
 * Concrete subclass of [AbstractKnowledgeEntityIngestionService] for notes. Owns
 * the note input shape (`title` / `content` / `plaintext` + attached entity ids)
 * and the mapping into the abstract relationship-batch contract — `ATTACHMENT`
 * edges, one per target entity. Used by both [NoteService] (user-authored notes)
 * and [NoteEmbeddingService] (integration-imported notes), so all paths funnel
 * through one ingestion seam.
 */
@Service
class NoteEntityIngestionService(
    entityIngestionService: EntityIngestionService,
    entityTypeRepository: EntityTypeRepository,
    entityRepository: EntityRepository,
    entityRelationshipRepository: EntityRelationshipRepository,
    entityTypeRelationshipService: EntityTypeRelationshipService,
    logger: KLogger,
) : AbstractKnowledgeEntityIngestionService<NoteEntityIngestionService.NoteIngestionInput>(
    entityIngestionService, entityTypeRepository, entityRepository, entityRelationshipRepository, entityTypeRelationshipService, logger,
) {

    override val entityTypeKey: String = KnowledgeEntityTypeKey.NOTE.key

    data class NoteIngestionInput(
        override val workspaceId: UUID,
        val title: String,
        val content: List<Map<String, Any>>,
        val plaintext: String,
        val targetEntityIds: Set<UUID> = emptySet(),
        override val sourceType: SourceType = SourceType.USER_CREATED,
        override val sourceIntegrationId: UUID? = null,
        override val sourceExternalId: String? = null,
        override val linkSource: SourceType = SourceType.USER_CREATED,
        override val existingId: UUID? = null,
        /**
         * Unresolved foreign references captured at sync time. When non-null, persisted
         * onto `entities.pending_associations` so the integration reconciliation pass can
         * retry resolution after sibling rows arrive. Set to an empty map to explicitly
         * clear the column on this upsert.
         */
        val pendingAssociations: Map<String, List<String>>? = null,
    ) : KnowledgeIngestionInput

    override fun buildAttributePayload(
        entityType: EntityTypeEntity,
        input: NoteIngestionInput,
    ): Map<UUID, Any?> = mapOf(
        attributeId(entityType, "title") to input.title,
        attributeId(entityType, "content") to input.content,
        attributeId(entityType, "plaintext") to input.plaintext,
    )

    override fun relationshipBatches(input: NoteIngestionInput): List<KnowledgeRelationshipBatch> =
        listOf(KnowledgeRelationshipBatch(SystemRelationshipType.ATTACHMENT, input.targetEntityIds))

    override fun postSave(saved: EntityEntity, input: NoteIngestionInput) {
        val newValue = input.pendingAssociations?.takeIf { it.isNotEmpty() }
        if (saved.pendingAssociations == newValue) return
        saved.pendingAssociations = newValue
        entityRepository.save(saved)
    }

    /**
     * Reconcile attachments + clear `pending_associations` on an existing note entity without
     * touching its attribute payload. Used by integration sync reconciliation passes that
     * resolve previously-unattached notes once sibling target rows arrive.
     */
    @org.springframework.transaction.annotation.Transactional
    open fun reconcileAttachments(
        workspaceId: UUID,
        entityId: UUID,
        targetEntityIds: Set<UUID>,
        linkSource: SourceType = SourceType.INTEGRATION,
    ) {
        val entity = entityRepository.findById(entityId).orElseThrow {
            IllegalStateException("Note entity $entityId not found for reconciliation")
        }
        require(entity.workspaceId == workspaceId) {
            "Note entity $entityId workspaceId=${entity.workspaceId} does not match expected $workspaceId"
        }
        val def = entityTypeRelationshipService.getOrCreateSystemDefinition(
            workspaceId, entity.typeId, SystemRelationshipType.ATTACHMENT,
        )
        entityIngestionService.replaceRelationshipsInternal(
            workspaceId = workspaceId,
            sourceEntityId = entityId,
            relationshipDefinitionId = requireNotNull(def.id) { "system relationship definition id must not be null" },
            targetIds = targetEntityIds,
            linkSource = linkSource,
        )
        if (entity.pendingAssociations != null) {
            entity.pendingAssociations = null
            entityRepository.save(entity)
        }
    }
}
