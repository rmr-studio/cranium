package riven.core.service.note

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import riven.core.entity.entity.EntityEntity
import riven.core.entity.note.NoteEntity
import riven.core.entity.note.NoteEntityAttachment
import riven.core.entity.note.NoteEntityAttachmentId
import riven.core.enums.note.NoteSourceType
import riven.core.models.integration.NangoRecord
import riven.core.models.integration.NangoRecordAction
import riven.core.models.integration.NangoRecordMetadata
import riven.core.models.note.NoteContentFormat
import riven.core.models.note.NoteEmbeddingConfig
import riven.core.repository.entity.EntityRepository
import riven.core.repository.note.NoteEntityAttachmentRepository
import riven.core.repository.note.NoteRepository
import riven.core.service.note.converter.HtmlToBlockConverter
import riven.core.service.note.converter.NoteConversionResult
import riven.core.service.note.converter.PlaintextToBlockConverter
import riven.core.service.util.factory.note.NoteFactory
import java.util.*

@ExtendWith(MockitoExtension::class)
class NoteEmbeddingServiceTest {

    @Mock private lateinit var noteRepository: NoteRepository
    @Mock private lateinit var attachmentRepository: NoteEntityAttachmentRepository
    @Mock private lateinit var entityRepository: EntityRepository
    @Mock private lateinit var htmlToBlockConverter: HtmlToBlockConverter
    @Mock private lateinit var plaintextToBlockConverter: PlaintextToBlockConverter
    @Mock private lateinit var logger: KLogger

    @Captor private lateinit var noteCaptor: ArgumentCaptor<NoteEntity>

    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var service: NoteEmbeddingService

    private val workspaceId = UUID.randomUUID()
    private val integrationId = UUID.randomUUID()

    private val config = NoteEmbeddingConfig(
        syncModel = "notes",
        bodyField = "hs_note_body",
        contentFormat = NoteContentFormat.HTML,
        associations = mapOf("contact" to "hubspot-contact"),
    )

    private val testBlocks = listOf(
        mapOf("type" to "paragraph", "content" to listOf(mapOf("type" to "text", "text" to "Hello")))
    )

    private val testConversionResult = NoteConversionResult(
        blocks = testBlocks,
        plaintext = "Hello",
        title = "Test Note",
    )

    @BeforeEach
    fun setUp() {
        // Use a TransactionTemplate that executes the callback directly (no real transaction)
        transactionTemplate = mock<TransactionTemplate>()
        whenever(transactionTemplate.execute(any<TransactionCallback<Unit>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val callback = invocation.arguments[0] as TransactionCallback<Unit>
            callback.doInTransaction(mock<TransactionStatus>())
        }

        service = NoteEmbeddingService(
            noteRepository,
            attachmentRepository,
            entityRepository,
            htmlToBlockConverter,
            plaintextToBlockConverter,
            transactionTemplate,
            logger,
        )
    }

    // ------ Helpers ------

    private fun nangoRecord(
        externalId: String = "ext-123",
        action: NangoRecordAction = NangoRecordAction.ADDED,
        payload: Map<String, Any?> = emptyMap(),
    ): NangoRecord {
        val metadata = NangoRecordMetadata(
            lastAction = action,
            cursor = "cursor-1",
        )
        val record = NangoRecord(nangoMetadata = metadata)
        payload.forEach { (key, value) -> record.setPayloadField(key, value) }
        return record
    }

    private fun stubHtmlConverter() {
        whenever(htmlToBlockConverter.convert(any())).thenReturn(testConversionResult)
    }

    private fun stubPlaintextConverter() {
        whenever(plaintextToBlockConverter.convert(any())).thenReturn(testConversionResult)
    }

    private fun stubNoteSave(): UUID {
        val noteId = UUID.randomUUID()
        whenever(noteRepository.save(any<NoteEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as NoteEntity
            if (entity.id != null) entity
            else NoteFactory.createEntity(
                id = noteId,
                workspaceId = entity.workspaceId,
                title = entity.title,
                content = entity.content,
                plaintext = entity.plaintext,
                sourceType = entity.sourceType,
                sourceIntegrationId = entity.sourceIntegrationId,
                sourceExternalId = entity.sourceExternalId,
                readonly = entity.readonly,
                pendingAssociations = entity.pendingAssociations,
            )
        }
        return noteId
    }

    // ------ Tests: ADDED Action ------

    @Nested
    inner class AddedAction {

        @Test
        fun `ADDED action creates NoteEntity with sourceType INTEGRATION and readonly true`() {
            stubHtmlConverter()
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)

            val record = nangoRecord(
                externalId = "ext-123",
                action = NangoRecordAction.ADDED,
                payload = mapOf("id" to "ext-123", "hs_note_body" to "<p>Hello</p>"),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            assertEquals(0, result.failed)

            verify(noteRepository).save(noteCaptor.capture())
            val savedNote = noteCaptor.allValues.first { it.id == null }
            assertEquals(NoteSourceType.INTEGRATION, savedNote.sourceType)
            assertTrue(savedNote.readonly)
            assertEquals("ext-123", savedNote.sourceExternalId)
            assertEquals(integrationId, savedNote.sourceIntegrationId)
            assertEquals(workspaceId, savedNote.workspaceId)
            assertEquals(NoteEmbeddingService.SYSTEM_USER_ID, savedNote.createdBy)
        }
    }

    // ------ Tests: UPDATED Action ------

    @Nested
    inner class UpdatedAction {

        @Test
        fun `UPDATED action updates content in place preserving sourceExternalId`() {
            stubHtmlConverter()
            val noteId = UUID.randomUUID()
            val existingNote = NoteFactory.createEntity(
                id = noteId,
                workspaceId = workspaceId,
                sourceType = NoteSourceType.INTEGRATION,
                sourceExternalId = "ext-123",
                sourceIntegrationId = integrationId,
                readonly = true,
            )
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(existingNote)
            whenever(noteRepository.save(any<NoteEntity>())).thenAnswer { it.arguments[0] }

            val record = nangoRecord(
                externalId = "ext-123",
                action = NangoRecordAction.UPDATED,
                payload = mapOf("id" to "ext-123", "hs_note_body" to "<p>Updated</p>"),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            verify(noteRepository).save(noteCaptor.capture())
            val saved = noteCaptor.value
            assertEquals(noteId, saved.id)
            assertEquals("ext-123", saved.sourceExternalId)
            assertEquals(testBlocks, saved.content)
            assertEquals("Hello", saved.plaintext)
            assertEquals(NoteEmbeddingService.SYSTEM_USER_ID, saved.updatedBy)
        }
    }

    // ------ Tests: DELETED Action ------

    @Nested
    inner class DeletedAction {

        @Test
        fun `DELETED action hard-deletes NoteEntity and all attachments`() {
            val noteId = UUID.randomUUID()
            val existingNote = NoteFactory.createEntity(
                id = noteId,
                workspaceId = workspaceId,
                sourceType = NoteSourceType.INTEGRATION,
                sourceExternalId = "ext-456",
            )
            whenever(noteRepository.findBySourceExternalId("ext-456")).thenReturn(existingNote)

            val record = nangoRecord(
                externalId = "ext-456",
                action = NangoRecordAction.DELETED,
                payload = mapOf("id" to "ext-456"),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            verify(attachmentRepository).deleteByNoteId(noteId)
            verify(noteRepository).delete(existingNote)
        }
    }

    // ------ Tests: Body Handling ------

    @Nested
    inner class BodyHandling {

        @Test
        fun `null body field skips record gracefully creating note with empty paragraph block`() {
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)

            val record = nangoRecord(
                payload = mapOf("id" to "ext-123"),
                // no hs_note_body field
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            verify(noteRepository).save(noteCaptor.capture())
            val saved = noteCaptor.allValues.first { it.id == null }
            assertEquals(1, saved.content.size)
            assertEquals("paragraph", saved.content[0]["type"])
            assertEquals("", saved.plaintext)

            // HTML converter should NOT be called for null body
            verify(htmlToBlockConverter, never()).convert(any())
        }

        @Test
        fun `empty body creates NoteEntity with empty paragraph block`() {
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)

            val record = nangoRecord(
                payload = mapOf("id" to "ext-123", "hs_note_body" to ""),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            verify(noteRepository).save(noteCaptor.capture())
            val saved = noteCaptor.allValues.first { it.id == null }
            assertEquals(1, saved.content.size)
            assertEquals("paragraph", saved.content[0]["type"])
            verify(htmlToBlockConverter, never()).convert(any())
        }

        @Test
        fun `body exceeding MAX_BODY_SIZE truncates to plaintext fallback`() {
            stubPlaintextConverter()
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)

            val longBody = "x".repeat(NoteEmbeddingService.MAX_BODY_SIZE + 1000)
            val record = nangoRecord(
                payload = mapOf("id" to "ext-123", "hs_note_body" to longBody),
            )

            service.processBatch(listOf(record), config, workspaceId, integrationId)

            // Should use plaintext converter, not HTML, and truncate
            verify(htmlToBlockConverter, never()).convert(any())
            verify(plaintextToBlockConverter).convert(argThat { length == NoteEmbeddingService.MAX_BODY_SIZE })
        }
    }

    // ------ Tests: Association Resolution ------

    @Nested
    inner class AssociationResolution {

        @Test
        fun `all targets resolved creates correct attachment rows`() {
            stubHtmlConverter()
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)
            whenever(attachmentRepository.save(any<NoteEntityAttachment>())).thenAnswer { it.arguments[0] }

            val entityId1 = UUID.randomUUID()
            val entityId2 = UUID.randomUUID()
            val entityEntity1 = mock<EntityEntity> { on { id } doReturn entityId1 }
            val entityEntity2 = mock<EntityEntity> { on { id } doReturn entityId2 }

            whenever(
                entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                    eq(workspaceId), eq(integrationId), any()
                )
            ).thenReturn(listOf(entityEntity1, entityEntity2))
            whenever(attachmentRepository.findByNoteId(noteId)).thenReturn(emptyList())

            val record = nangoRecord(
                payload = mapOf(
                    "id" to "ext-123",
                    "hs_note_body" to "<p>Hello</p>",
                    "associations" to mapOf("contact" to listOf("contact-1", "contact-2")),
                ),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            assertEquals(0, result.failed)
            verify(attachmentRepository, times(2)).save(any<NoteEntityAttachment>())
        }

        @Test
        fun `some targets missing creates partial attachments for resolved targets`() {
            /**
             * When some association targets resolve but others don't (e.g. contact synced
             * but deal hasn't yet), the resolved targets get attachment rows immediately.
             * Missing targets will be resolved during post-sync reconciliation.
             */
            stubHtmlConverter()
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)
            whenever(attachmentRepository.save(any<NoteEntityAttachment>())).thenAnswer { it.arguments[0] }

            val entityId1 = UUID.randomUUID()
            val entityEntity1 = mock<EntityEntity> { on { id } doReturn entityId1 }

            // Only one of two contacts resolves
            whenever(
                entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                    eq(workspaceId), eq(integrationId), any()
                )
            ).thenReturn(listOf(entityEntity1))
            whenever(attachmentRepository.findByNoteId(noteId)).thenReturn(emptyList())

            val record = nangoRecord(
                payload = mapOf(
                    "id" to "ext-123",
                    "hs_note_body" to "<p>Hello</p>",
                    "associations" to mapOf("contact" to listOf("contact-1", "contact-2")),
                ),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            // One attachment created for the resolved entity
            verify(attachmentRepository).save(any<NoteEntityAttachment>())
        }

        @Test
        fun `no targets found stores pending_associations with zero attachments`() {
            stubHtmlConverter()
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)

            // No entities resolve
            whenever(
                entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                    eq(workspaceId), eq(integrationId), any()
                )
            ).thenReturn(emptyList())

            val record = nangoRecord(
                payload = mapOf(
                    "id" to "ext-123",
                    "hs_note_body" to "<p>Hello</p>",
                    "associations" to mapOf("contact" to listOf("contact-1")),
                ),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            // No attachments created
            verify(attachmentRepository, never()).save(any<NoteEntityAttachment>())
            // Note saved with pending_associations
            verify(noteRepository, atLeast(2)).save(noteCaptor.capture())
            val pendingSave = noteCaptor.allValues.last()
            assertNotNull(pendingSave.pendingAssociations)
            assertEquals(listOf("contact-1"), pendingSave.pendingAssociations?.get("contact"))
        }

        @Test
        fun `empty associations map creates NoteEntity with zero attachments`() {
            stubHtmlConverter()
            val noteId = stubNoteSave()
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(null)

            val configNoAssoc = config.copy(associations = emptyMap())

            val record = nangoRecord(
                payload = mapOf(
                    "id" to "ext-123",
                    "hs_note_body" to "<p>Hello</p>",
                    "associations" to mapOf("contact" to listOf("contact-1")),
                ),
            )

            val result = service.processBatch(listOf(record), configNoAssoc, workspaceId, integrationId)

            assertEquals(1, result.synced)
            verify(attachmentRepository, never()).save(any<NoteEntityAttachment>())
            verify(entityRepository, never())
                .findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(any(), any(), any())
        }
    }

    // ------ Tests: Error Isolation ------

    @Nested
    inner class ErrorIsolation {

        @Test
        fun `per-record error isolation allows other records to succeed`() {
            stubHtmlConverter()
            val noteId = stubNoteSave()

            // First record will throw (missing 'id' field)
            val badRecord = nangoRecord(
                payload = mapOf("hs_note_body" to "<p>Bad</p>"),
                // no "id" field -> IllegalArgumentException
            )

            // Second record is valid
            val goodRecord = nangoRecord(
                externalId = "ext-good",
                payload = mapOf("id" to "ext-good", "hs_note_body" to "<p>Good</p>"),
            )
            whenever(noteRepository.findBySourceExternalId("ext-good")).thenReturn(null)

            val result = service.processBatch(listOf(badRecord, goodRecord), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            assertEquals(1, result.failed)
            assertNotNull(result.lastError)
        }
    }

    // ------ Tests: Idempotent Attachment Sync ------

    @Nested
    inner class IdempotentAttachmentSync {

        @Test
        fun `idempotent attachment sync adds new and removes stale attachments`() {
            /**
             * When a note's associations change on update, syncAttachments diffs the existing
             * attachment rows against the newly resolved targets: adds missing, removes stale,
             * and leaves existing untouched.
             */
            stubHtmlConverter()
            val noteId = UUID.randomUUID()
            val existingNote = NoteFactory.createEntity(
                id = noteId,
                workspaceId = workspaceId,
                sourceType = NoteSourceType.INTEGRATION,
                sourceExternalId = "ext-123",
                sourceIntegrationId = integrationId,
                readonly = true,
            )
            whenever(noteRepository.findBySourceExternalId("ext-123")).thenReturn(existingNote)
            whenever(noteRepository.save(any<NoteEntity>())).thenAnswer { it.arguments[0] }
            whenever(attachmentRepository.save(any<NoteEntityAttachment>())).thenAnswer { it.arguments[0] }

            val keepEntityId = UUID.randomUUID()
            val removeEntityId = UUID.randomUUID()
            val addEntityId = UUID.randomUUID()

            // Existing attachments: keepEntityId + removeEntityId
            whenever(attachmentRepository.findByNoteId(noteId)).thenReturn(
                listOf(
                    NoteFactory.createAttachment(noteId = noteId, entityId = keepEntityId),
                    NoteFactory.createAttachment(noteId = noteId, entityId = removeEntityId),
                )
            )

            // Resolved targets: keepEntityId + addEntityId (removeEntityId dropped, addEntityId is new)
            val keepEntity = mock<EntityEntity> { on { id } doReturn keepEntityId }
            val addEntity = mock<EntityEntity> { on { id } doReturn addEntityId }
            whenever(
                entityRepository.findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
                    eq(workspaceId), eq(integrationId), any()
                )
            ).thenReturn(listOf(keepEntity, addEntity))

            val record = nangoRecord(
                externalId = "ext-123",
                action = NangoRecordAction.UPDATED,
                payload = mapOf(
                    "id" to "ext-123",
                    "hs_note_body" to "<p>Updated</p>",
                    "associations" to mapOf("contact" to listOf("keep-ext", "add-ext")),
                ),
            )

            val result = service.processBatch(listOf(record), config, workspaceId, integrationId)

            assertEquals(1, result.synced)
            // Should add the new attachment
            verify(attachmentRepository).save(argThat<NoteEntityAttachment> { this.entityId == addEntityId })
            // Should remove the stale attachment
            verify(attachmentRepository).deleteById(eq(NoteEntityAttachmentId(noteId, removeEntityId)))
            // Should NOT re-add the already-existing attachment
            verify(attachmentRepository, never()).save(argThat<NoteEntityAttachment> { this.entityId == keepEntityId })
        }
    }
}
