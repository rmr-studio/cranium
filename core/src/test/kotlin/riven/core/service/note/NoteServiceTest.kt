package riven.core.service.note

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.note.NoteEntity
import riven.core.entity.note.NoteEntityAttachment
import riven.core.enums.note.NoteSourceType
import riven.core.exceptions.NotFoundException
import riven.core.models.note.CreateNoteRequest
import riven.core.models.note.UpdateNoteRequest
import riven.core.repository.note.NoteEntityAttachmentRepository
import riven.core.repository.note.NoteRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.note.NoteFactory
import riven.core.util.CursorPagination
import java.time.ZonedDateTime
import java.util.*

@SpringBootTest(classes = [AuthTokenService::class, WorkspaceSecurity::class, SecurityTestConfig::class, NoteService::class])
class NoteServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var noteRepository: NoteRepository

    @MockitoBean
    private lateinit var attachmentRepository: NoteEntityAttachmentRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var noteService: NoteService

    private val entityId: UUID = UUID.randomUUID()

    // ------------------------------------------------------------------
    // getNotesForEntity
    // ------------------------------------------------------------------

    @Test
    fun `getNotesForEntity returns notes ordered by createdAt desc`() {
        val note1 = NoteFactory.createEntity(entityId = entityId, workspaceId = workspaceId, title = "Note 1")
        val note2 = NoteFactory.createEntity(id = UUID.randomUUID(), entityId = entityId, workspaceId = workspaceId, title = "Note 2")

        whenever(noteRepository.findByEntityIdAndWorkspaceId(entityId, workspaceId))
            .thenReturn(listOf(note2, note1))
        whenever(attachmentRepository.findByNoteIdIn(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST") val ids = inv.arguments[0] as List<UUID>
            ids.map { NoteFactory.createAttachment(noteId = it, entityId = entityId) }
        }

        val result = noteService.getNotesForEntity(workspaceId, entityId)

        assertEquals(2, result.size)
        assertEquals("Note 2", result[0].title)
        assertEquals("Note 1", result[1].title)
        assertTrue(result[0].entityIds.contains(entityId))
    }

    @Test
    fun `getNotesForEntity with search delegates to search query`() {
        val note = NoteFactory.createEntity(entityId = entityId, workspaceId = workspaceId, title = "Found")

        whenever(noteRepository.searchByEntityIdAndWorkspaceId(entityId, workspaceId, "test"))
            .thenReturn(listOf(note))
        whenever(attachmentRepository.findByNoteIdIn(any())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST") val ids = inv.arguments[0] as List<UUID>
            ids.map { NoteFactory.createAttachment(noteId = it, entityId = entityId) }
        }

        val result = noteService.getNotesForEntity(workspaceId, entityId, "test")

        assertEquals(1, result.size)
        assertEquals("Found", result[0].title)
        verify(noteRepository, never()).findByEntityIdAndWorkspaceId(any(), any())
    }

    // ------------------------------------------------------------------
    // createNote
    // ------------------------------------------------------------------

    @Test
    fun `createNote saves entity and attachment row and logs activity`() {
        /**
         * Regression test: createNote must create both a NoteEntity AND a
         * note_entity_attachment row for the target entity.
         */
        val request = CreateNoteRequest(
            content = listOf(
                mapOf(
                    "type" to "paragraph",
                    "content" to listOf(mapOf("type" to "text", "text" to "Hello world"))
                )
            )
        )

        val saved = NoteFactory.createEntity(
            entityId = entityId,
            workspaceId = workspaceId,
            title = "Hello world",
            plaintext = "Hello world",
            content = request.content,
        )

        whenever(noteRepository.save(any<NoteEntity>())).thenReturn(saved)
        whenever(attachmentRepository.save(any<NoteEntityAttachment>())).thenAnswer { it.arguments[0] }

        val result = noteService.createNote(workspaceId, entityId, request)

        assertEquals("Hello world", result.title)
        assertTrue(result.entityIds.contains(entityId))
        verify(noteRepository).save(any<NoteEntity>())
        verify(attachmentRepository).save(argThat<NoteEntityAttachment> {
            this.noteId == NoteFactory.DEFAULT_NOTE_ID && this.entityId == entityId
        })
        verify(activityService).logActivity(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `createNote extracts title from content when title not provided`() {
        val request = CreateNoteRequest(
            content = listOf(
                mapOf(
                    "type" to "paragraph",
                    "content" to listOf(mapOf("type" to "text", "text" to "Auto title"))
                )
            )
        )

        val saved = NoteFactory.createEntity(
            entityId = entityId,
            workspaceId = workspaceId,
            title = "Auto title",
            content = request.content,
        )
        whenever(noteRepository.save(any<NoteEntity>())).thenReturn(saved)
        whenever(attachmentRepository.save(any<NoteEntityAttachment>())).thenAnswer { it.arguments[0] }

        val result = noteService.createNote(workspaceId, entityId, request)
        assertEquals("Auto title", result.title)
    }

    // ------------------------------------------------------------------
    // updateNote
    // ------------------------------------------------------------------

    @Test
    fun `updateNote updates content and extracts new plaintext`() {
        val existing = NoteFactory.createEntity(entityId = entityId, workspaceId = workspaceId)

        whenever(noteRepository.findById(NoteFactory.DEFAULT_NOTE_ID)).thenReturn(Optional.of(existing))
        whenever(noteRepository.save(any<NoteEntity>())).thenAnswer { it.arguments[0] as NoteEntity }
        whenever(attachmentRepository.findEntityIdsByNoteId(NoteFactory.DEFAULT_NOTE_ID)).thenReturn(listOf(entityId))

        val newContent = listOf(
            mapOf<String, Any>(
                "type" to "paragraph",
                "content" to listOf(mapOf("type" to "text", "text" to "Updated content"))
            )
        )

        val result = noteService.updateNote(workspaceId, NoteFactory.DEFAULT_NOTE_ID, UpdateNoteRequest(content = newContent))

        assertEquals("Updated content", result.title)
        verify(noteRepository).save(any<NoteEntity>())
    }

    @Test
    fun `updateNote throws NotFoundException for missing note`() {
        whenever(noteRepository.findById(any<UUID>())).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            noteService.updateNote(workspaceId, UUID.randomUUID(), UpdateNoteRequest(title = "x"))
        }
    }

    @Test
    fun `updateNote rejects note from different workspace`() {
        val otherWorkspace = UUID.randomUUID()
        val existing = NoteFactory.createEntity(entityId = entityId, workspaceId = otherWorkspace)

        whenever(noteRepository.findById(NoteFactory.DEFAULT_NOTE_ID)).thenReturn(Optional.of(existing))

        assertThrows<IllegalArgumentException> {
            noteService.updateNote(workspaceId, NoteFactory.DEFAULT_NOTE_ID, UpdateNoteRequest(title = "x"))
        }
    }

    @Test
    fun `updateNote throws AccessDeniedException for readonly note`() {
        /**
         * Bug prevention: readonly notes (from integration sync) must not be
         * modifiable via the user-facing update endpoint.
         */
        val existing = NoteFactory.createEntity(
            entityId = entityId,
            workspaceId = workspaceId,
            readonly = true,
            sourceType = NoteSourceType.INTEGRATION,
        )

        whenever(noteRepository.findById(NoteFactory.DEFAULT_NOTE_ID)).thenReturn(Optional.of(existing))

        assertThrows<AccessDeniedException> {
            noteService.updateNote(workspaceId, NoteFactory.DEFAULT_NOTE_ID, UpdateNoteRequest(title = "x"))
        }
    }

    // ------------------------------------------------------------------
    // deleteNote
    // ------------------------------------------------------------------

    @Test
    fun `deleteNote removes entity and logs activity`() {
        val existing = NoteFactory.createEntity(entityId = entityId, workspaceId = workspaceId)

        whenever(noteRepository.findById(NoteFactory.DEFAULT_NOTE_ID)).thenReturn(Optional.of(existing))

        noteService.deleteNote(workspaceId, NoteFactory.DEFAULT_NOTE_ID)

        verify(noteRepository).delete(existing)
        verify(activityService).logActivity(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `deleteNote throws NotFoundException for missing note`() {
        whenever(noteRepository.findById(any<UUID>())).thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            noteService.deleteNote(workspaceId, UUID.randomUUID())
        }
    }

    @Test
    fun `deleteNote throws AccessDeniedException for readonly note`() {
        /**
         * Bug prevention: readonly notes (from integration sync) must not be
         * deletable via the user-facing delete endpoint.
         */
        val existing = NoteFactory.createEntity(
            entityId = entityId,
            workspaceId = workspaceId,
            readonly = true,
            sourceType = NoteSourceType.INTEGRATION,
        )

        whenever(noteRepository.findById(NoteFactory.DEFAULT_NOTE_ID)).thenReturn(Optional.of(existing))

        assertThrows<AccessDeniedException> {
            noteService.deleteNote(workspaceId, NoteFactory.DEFAULT_NOTE_ID)
        }
    }

    // ------------------------------------------------------------------
    // getWorkspaceNotes
    // ------------------------------------------------------------------

    @Test
    fun `getWorkspaceNotes returns first page with enrichment and nextCursor`() {
        val note1 = NoteFactory.createEntity(workspaceId = workspaceId, entityId = entityId, title = "Note 1")
            .also { it.createdAt = ZonedDateTime.now().minusHours(2) }
        val note2 = NoteFactory.createEntity(id = UUID.randomUUID(), workspaceId = workspaceId, entityId = entityId, title = "Note 2")
            .also { it.createdAt = ZonedDateTime.now().minusHours(1) }

        whenever(noteRepository.findByWorkspaceId(eq(workspaceId), any(), any(), any()))
            .thenReturn(listOf(note1, note2))
        whenever(noteRepository.countByWorkspaceId(workspaceId)).thenReturn(5L)
        whenever(attachmentRepository.findByNoteIdIn(any()))
            .thenReturn(listOf(NoteFactory.createAttachment(entityId = entityId)))
        whenever(noteRepository.findEntityContext(any()))
            .thenReturn(listOf(
                arrayOf<Any?>(entityId, "Acme Corp", "company", "BUILDING", "BLUE")
            ))

        val result = noteService.getWorkspaceNotes(workspaceId, limit = 2)

        assertEquals(2, result.items.size)
        assertEquals(5L, result.totalCount)
        assertNotNull(result.nextCursor)

        val firstContext = result.items[0].entityContexts.first()
        assertEquals("Acme Corp", firstContext.entityDisplayName)
        assertEquals("company", firstContext.entityTypeKey)
        assertEquals("BUILDING", firstContext.entityTypeIcon)
        assertEquals("BLUE", firstContext.entityTypeColour)
    }

    @Test
    fun `getWorkspaceNotes returns null nextCursor on last page`() {
        val note = NoteFactory.createEntity(workspaceId = workspaceId, entityId = entityId)

        whenever(noteRepository.findByWorkspaceId(eq(workspaceId), any(), any(), any()))
            .thenReturn(listOf(note))
        whenever(noteRepository.countByWorkspaceId(workspaceId)).thenReturn(1L)
        whenever(attachmentRepository.findByNoteIdIn(any()))
            .thenReturn(listOf(NoteFactory.createAttachment(entityId = entityId)))
        whenever(noteRepository.findEntityContext(any()))
            .thenReturn(listOf(arrayOf<Any?>(entityId, "Acme", "company", "BUILDING", "BLUE")))

        val result = noteService.getWorkspaceNotes(workspaceId, limit = 20)

        assertNull(result.nextCursor)
        assertEquals(1, result.items.size)
    }

    @Test
    fun `getWorkspaceNotes with search uses search repository method`() {
        whenever(noteRepository.searchByWorkspaceId(eq(workspaceId), eq("meeting"), any(), any(), eq(20)))
            .thenReturn(emptyList())
        whenever(noteRepository.countByWorkspaceId(workspaceId)).thenReturn(0L)

        val result = noteService.getWorkspaceNotes(workspaceId, search = "meeting")

        assertEquals(0, result.items.size)
        verify(noteRepository).searchByWorkspaceId(eq(workspaceId), eq("meeting"), any(), any(), eq(20))
        verify(noteRepository, never()).findByWorkspaceId(any(), any(), any(), any())
    }

    @Test
    fun `getWorkspaceNotes with cursor decodes and passes to repository`() {
        val cursorTime = ZonedDateTime.parse("2026-03-20T10:00:00Z")
        val cursorId = UUID.randomUUID()
        val cursor = CursorPagination.encodeCursor(cursorTime, cursorId)

        whenever(noteRepository.findByWorkspaceId(eq(workspaceId), eq(cursorTime), eq(cursorId), any()))
            .thenReturn(emptyList())
        whenever(noteRepository.countByWorkspaceId(workspaceId)).thenReturn(0L)

        noteService.getWorkspaceNotes(workspaceId, cursor = cursor)

        verify(noteRepository).findByWorkspaceId(eq(workspaceId), eq(cursorTime), eq(cursorId), any())
    }

    @Test
    fun `getWorkspaceNotes returns empty list for workspace with no notes`() {
        whenever(noteRepository.findByWorkspaceId(eq(workspaceId), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(noteRepository.countByWorkspaceId(workspaceId)).thenReturn(0L)

        val result = noteService.getWorkspaceNotes(workspaceId)

        assertEquals(0, result.items.size)
        assertNull(result.nextCursor)
        assertEquals(0L, result.totalCount)
    }

    @Test
    fun `getWorkspaceNotes rejects invalid limit`() {
        assertThrows<IllegalArgumentException> {
            noteService.getWorkspaceNotes(workspaceId, limit = 0)
        }
        assertThrows<IllegalArgumentException> {
            noteService.getWorkspaceNotes(workspaceId, limit = 101)
        }
    }

    @Test
    fun `getWorkspaceNotes handles null entityDisplayName when identifier attribute missing`() {
        val note = NoteFactory.createEntity(workspaceId = workspaceId, entityId = entityId)

        whenever(noteRepository.findByWorkspaceId(eq(workspaceId), any(), any(), any()))
            .thenReturn(listOf(note))
        whenever(noteRepository.countByWorkspaceId(workspaceId)).thenReturn(1L)
        whenever(attachmentRepository.findByNoteIdIn(any()))
            .thenReturn(listOf(NoteFactory.createAttachment(entityId = entityId)))
        whenever(noteRepository.findEntityContext(any()))
            .thenReturn(listOf(arrayOf<Any?>(entityId, null, "company", "BUILDING", "BLUE")))

        val result = noteService.getWorkspaceNotes(workspaceId, limit = 20)

        val firstContext = result.items[0].entityContexts.first()
        assertNull(firstContext.entityDisplayName)
        assertEquals("company", firstContext.entityTypeKey)
    }

    @Test
    fun `getWorkspaceNotes handles entity with soft-deleted parent gracefully`() {
        /**
         * When an entity is soft-deleted, findEntityContext excludes it (WHERE e.deleted = false).
         * Notes referencing deleted entities should still return but with empty entity contexts.
         */
        val deletedEntityId = UUID.randomUUID()
        val note = NoteFactory.createEntity(workspaceId = workspaceId, entityId = deletedEntityId)

        whenever(noteRepository.findByWorkspaceId(eq(workspaceId), any(), any(), any()))
            .thenReturn(listOf(note))
        whenever(noteRepository.countByWorkspaceId(workspaceId)).thenReturn(1L)
        whenever(attachmentRepository.findByNoteIdIn(any()))
            .thenReturn(listOf(NoteFactory.createAttachment(entityId = deletedEntityId)))
        whenever(noteRepository.findEntityContext(any())).thenReturn(emptyList())

        val result = noteService.getWorkspaceNotes(workspaceId, limit = 20)

        assertEquals(1, result.items.size)
        assertTrue(result.items[0].entityContexts.isEmpty())
    }

    // ------------------------------------------------------------------
    // getWorkspaceNote
    // ------------------------------------------------------------------

    @Test
    fun `getWorkspaceNote returns enriched note`() {
        val note = NoteFactory.createEntity(workspaceId = workspaceId, entityId = entityId)

        whenever(noteRepository.findByIdAndWorkspaceId(NoteFactory.DEFAULT_NOTE_ID, workspaceId))
            .thenReturn(note)
        whenever(attachmentRepository.findByNoteIdIn(listOf(NoteFactory.DEFAULT_NOTE_ID)))
            .thenReturn(listOf(NoteFactory.createAttachment(entityId = entityId)))
        whenever(noteRepository.findEntityContext(any()))
            .thenReturn(listOf(arrayOf<Any?>(entityId, "Acme Corp", "company", "BUILDING", "BLUE")))

        val result = noteService.getWorkspaceNote(workspaceId, NoteFactory.DEFAULT_NOTE_ID)

        assertEquals(NoteFactory.DEFAULT_NOTE_ID, result.id)
        assertEquals("Acme Corp", result.entityContexts.first().entityDisplayName)
    }

    @Test
    fun `getWorkspaceNote throws NotFoundException for missing note`() {
        whenever(noteRepository.findByIdAndWorkspaceId(any(), any())).thenReturn(null)

        assertThrows<NotFoundException> {
            noteService.getWorkspaceNote(workspaceId, UUID.randomUUID())
        }
    }

    @Test
    fun `getWorkspaceNote returns not found for note in different workspace`() {
        val noteId = UUID.randomUUID()
        whenever(noteRepository.findByIdAndWorkspaceId(noteId, workspaceId)).thenReturn(null)

        assertThrows<NotFoundException> {
            noteService.getWorkspaceNote(workspaceId, noteId)
        }
    }

    // ------------------------------------------------------------------
    // Regression: getNotesForEntity queries via join table
    // ------------------------------------------------------------------

    @Test
    fun `getNotesForEntity queries via join table and returns correct entityIds`() {
        /**
         * Regression test: entity-scoped note queries must go through the
         * note_entity_attachments join table, not the denormalized entity_id column.
         */
        val secondEntityId = UUID.randomUUID()
        val note = NoteFactory.createEntity(entityId = entityId, workspaceId = workspaceId)

        whenever(noteRepository.findByEntityIdAndWorkspaceId(entityId, workspaceId))
            .thenReturn(listOf(note))
        whenever(attachmentRepository.findByNoteIdIn(any())).thenReturn(
            listOf(
                NoteFactory.createAttachment(noteId = NoteFactory.DEFAULT_NOTE_ID, entityId = entityId),
                NoteFactory.createAttachment(noteId = NoteFactory.DEFAULT_NOTE_ID, entityId = secondEntityId),
            )
        )

        val result = noteService.getNotesForEntity(workspaceId, entityId)

        assertEquals(1, result.size)
        assertEquals(2, result[0].entityIds.size)
        assertTrue(result[0].entityIds.containsAll(listOf(entityId, secondEntityId)))
    }
}
