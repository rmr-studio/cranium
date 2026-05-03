package riven.core.service.note

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
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
}
