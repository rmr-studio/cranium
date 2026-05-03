package riven.core.service.note

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import riven.core.enums.entity.EntityRelationshipCardinality
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.enums.entity.SystemRelationshipType
import riven.core.enums.integration.SourceType
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.service.entity.EntityIngestionService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.util.factory.entity.EntityFactory
import java.util.Optional
import java.util.UUID

/**
 * Verifies the note-specific subclass wires the abstract base correctly:
 * three attribute keys (`title`, `content`, `plaintext`) flow through and
 * the note's targets become a single `ATTACHMENT` relationship batch.
 */
class NoteEntityIngestionServiceTest {

    private val workspaceId: UUID = UUID.randomUUID()
    private val entityTypeId: UUID = UUID.randomUUID()
    private val titleAttrId: UUID = UUID.randomUUID()
    private val contentAttrId: UUID = UUID.randomUUID()
    private val plaintextAttrId: UUID = UUID.randomUUID()
    private val systemDefinitionId: UUID = UUID.randomUUID()

    private val entityIngestionService: EntityIngestionService = mock()
    private val entityTypeRepository: EntityTypeRepository = mock()
    private val entityRepository: EntityRepository = mock()
    private val entityRelationshipRepository: EntityRelationshipRepository = mock()
    private val entityTypeRelationshipService: EntityTypeRelationshipService = mock()
    private val logger: KLogger = mock()

    private val service = NoteEntityIngestionService(
        entityIngestionService, entityTypeRepository, entityRepository, entityRelationshipRepository, entityTypeRelationshipService, logger,
    )

    @Test
    fun `upsert maps title, content, plaintext into attribute payload and emits ATTACHMENT batch per target`() {
        val noteType = EntityFactory.createEntityType(
            id = entityTypeId,
            key = "note",
            workspaceId = workspaceId,
            attributeKeyMapping = mapOf(
                "title" to titleAttrId.toString(),
                "content" to contentAttrId.toString(),
                "plaintext" to plaintextAttrId.toString(),
            ),
        )
        whenever(entityTypeRepository.findByworkspaceIdAndKey(workspaceId, "note"))
            .thenReturn(Optional.of(noteType))

        val def = EntityFactory.createRelationshipDefinitionEntity(
            id = systemDefinitionId,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
            name = "Attachments",
            cardinalityDefault = EntityRelationshipCardinality.MANY_TO_MANY,
            protected = true,
            systemType = SystemRelationshipType.ATTACHMENT,
        )
        whenever(
            entityTypeRelationshipService.getOrCreateSystemDefinition(
                workspaceId, entityTypeId, SystemRelationshipType.ATTACHMENT,
            )
        ).thenReturn(def)

        val savedEntity = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(), workspaceId = workspaceId, typeId = entityTypeId, typeKey = "note",
        )
        whenever(
            entityIngestionService.saveEntityInternal(
                workspaceId = any(), entityTypeId = any(), existingId = anyOrNull(),
                attributePayload = any(), sourceType = any(), sourceIntegrationId = anyOrNull(),
                sourceExternalId = anyOrNull(),
            )
        ).thenReturn(savedEntity)

        val target1 = UUID.randomUUID()
        val target2 = UUID.randomUUID()
        val input = NoteEntityIngestionService.NoteIngestionInput(
            workspaceId = workspaceId,
            title = "Status Update",
            content = listOf(mapOf("type" to "paragraph")),
            plaintext = "Status Update",
            targetEntityIds = setOf(target1, target2),
        )

        val saved = service.upsert(input)

        assertThat(saved.id).isEqualTo(savedEntity.id)
        val payloadCaptor = argumentCaptor<Map<UUID, Any?>>()
        verify(entityIngestionService).saveEntityInternal(
            workspaceId = eq(workspaceId),
            entityTypeId = eq(entityTypeId),
            existingId = anyOrNull(),
            attributePayload = payloadCaptor.capture(),
            sourceType = eq(SourceType.USER_CREATED),
            sourceIntegrationId = anyOrNull(),
            sourceExternalId = anyOrNull(),
        )
        assertThat(payloadCaptor.firstValue.keys).containsExactlyInAnyOrder(
            titleAttrId, contentAttrId, plaintextAttrId,
        )
        assertThat(payloadCaptor.firstValue[titleAttrId]).isEqualTo("Status Update")
        assertThat(payloadCaptor.firstValue[plaintextAttrId]).isEqualTo("Status Update")

        verify(entityIngestionService).replaceRelationshipsInternal(
            workspaceId = eq(workspaceId),
            sourceEntityId = eq(requireNotNull(savedEntity.id)),
            relationshipDefinitionId = eq(systemDefinitionId),
            targetIds = eq(setOf(target1, target2)),
            linkSource = eq(SourceType.USER_CREATED),
            targetKind = eq(RelationshipTargetKind.ENTITY),
            targetParentId = eq(null),
        )
    }
}
