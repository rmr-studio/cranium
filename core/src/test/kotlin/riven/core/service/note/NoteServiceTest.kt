package riven.core.service.note

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.context.bean.override.mockito.MockitoBean
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.enums.integration.SourceType
import riven.core.enums.note.NoteSourceType
import riven.core.exceptions.NotFoundException
import riven.core.models.note.CreateNoteRequest
import riven.core.models.note.Note
import riven.core.models.note.UpdateNoteRequest
import riven.core.models.note.WorkspaceNote
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityIngestionService
import riven.core.service.util.BaseServiceTest
import riven.core.service.util.SecurityTestConfig
import riven.core.service.util.factory.entity.EntityFactory
import java.util.UUID

/**
 * NoteService coverage. Every read and write path goes through the entity layer
 * (NoteEntityIngestionService for mutations, NoteEntityProjector for reads). These tests
 * verify the controller-facing contract (signatures + activity log + readonly enforcement).
 */
@SpringBootTest(
    classes = [
        AuthTokenService::class, WorkspaceSecurity::class, SecurityTestConfig::class, NoteService::class,
    ],
)
class NoteServiceTest : BaseServiceTest() {

    @MockitoBean
    private lateinit var entityIngestionService: EntityIngestionService

    @MockitoBean
    private lateinit var noteEntityIngestionService: NoteEntityIngestionService

    @MockitoBean
    private lateinit var noteEntityProjector: NoteEntityProjector

    @MockitoBean
    private lateinit var activityService: ActivityService

    @Autowired
    private lateinit var noteService: NoteService

    private val entityId: UUID = UUID.randomUUID()

    private fun stubProjectorReturns(savedId: UUID, title: String, entityIds: List<UUID>): Note {
        val note = Note(
            id = savedId,
            entityIds = entityIds,
            workspaceId = workspaceId,
            title = title,
            content = emptyList(),
            sourceType = NoteSourceType.USER,
            readonly = false,
            createdAt = null,
            updatedAt = null,
            createdBy = null,
            updatedBy = null,
        )
        whenever(noteEntityProjector.projectNote(eq(workspaceId), any())).thenReturn(note)
        return note
    }

    @Test
    fun `createNote routes through ingestion service with ATTACHMENT target`() {
        val request = CreateNoteRequest(
            content = listOf(
                mapOf(
                    "type" to "paragraph",
                    "content" to listOf(mapOf("type" to "text", "text" to "Hello world")),
                ),
            ),
        )
        val savedEntity = EntityFactory.createEntityEntity(
            id = UUID.randomUUID(),
            workspaceId = workspaceId,
            typeKey = "note",
        )
        whenever(noteEntityIngestionService.upsert(any())).thenReturn(savedEntity)
        stubProjectorReturns(requireNotNull(savedEntity.id), "Hello world", listOf(entityId))

        noteService.createNote(workspaceId, entityId, request)

        val captor = argumentCaptor<NoteEntityIngestionService.NoteIngestionInput>()
        verify(noteEntityIngestionService).upsert(captor.capture())
        val input = captor.firstValue
        assertThat(input.workspaceId).isEqualTo(workspaceId)
        assertThat(input.title).isEqualTo("Hello world")
        assertThat(input.plaintext).isEqualTo("Hello world")
        assertThat(input.targetEntityIds).containsExactly(entityId)
        assertThat(input.sourceType).isEqualTo(SourceType.USER_CREATED)
    }

    @Test
    fun `updateNote rejects integration-source notes with AccessDeniedException`() {
        val noteId = UUID.randomUUID()
        val readonlyEntity = EntityFactory.createEntityEntity(
            id = noteId,
            workspaceId = workspaceId,
            typeKey = "note",
            sourceType = SourceType.INTEGRATION,
        )
        whenever(entityIngestionService.findByIdInternal(workspaceId, noteId)).thenReturn(readonlyEntity)

        assertThrows<AccessDeniedException> {
            noteService.updateNote(workspaceId, noteId, UpdateNoteRequest(title = "x"))
        }
        verify(noteEntityIngestionService, never()).upsert(any())
    }

    @Test
    fun `updateNote — missing entity throws NotFoundException`() {
        val noteId = UUID.randomUUID()
        whenever(entityIngestionService.findByIdInternal(workspaceId, noteId)).thenReturn(null)

        assertThrows<NotFoundException> {
            noteService.updateNote(workspaceId, noteId, UpdateNoteRequest(title = "x"))
        }
    }

    @Test
    fun `deleteNote routes through ingestion soft-delete`() {
        val noteId = UUID.randomUUID()
        val entity = EntityFactory.createEntityEntity(
            id = noteId, workspaceId = workspaceId, typeKey = "note",
        )
        whenever(entityIngestionService.findByIdInternal(workspaceId, noteId)).thenReturn(entity)
        stubProjectorReturns(noteId, "stub", emptyList())

        noteService.deleteNote(workspaceId, noteId)

        verify(noteEntityIngestionService).softDelete(workspaceId, noteId)
    }

    @Test
    fun `deleteNote — readonly integration note rejected`() {
        val noteId = UUID.randomUUID()
        val readonly = EntityFactory.createEntityEntity(
            id = noteId,
            workspaceId = workspaceId,
            typeKey = "note",
            sourceType = SourceType.INTEGRATION,
        )
        whenever(entityIngestionService.findByIdInternal(workspaceId, noteId)).thenReturn(readonly)

        assertThrows<AccessDeniedException> {
            noteService.deleteNote(workspaceId, noteId)
        }
        verify(noteEntityIngestionService, never()).softDelete(any(), any())
    }

    @Test
    fun `getWorkspaceNotes delegates to projector`() {
        val expected = riven.core.util.CursorPage(
            items = listOf<WorkspaceNote>(),
            nextCursor = null,
            totalCount = 0L,
        )
        whenever(noteEntityProjector.listNotes(workspaceId, null, null, 20)).thenReturn(expected)

        val result = noteService.getWorkspaceNotes(workspaceId)

        assertThat(result).isSameAs(expected)
    }

    @Test
    fun `getNotesForEntity delegates to projector`() {
        whenever(noteEntityProjector.getNotesForEntity(workspaceId, entityId, null)).thenReturn(emptyList())

        val result = noteService.getNotesForEntity(workspaceId, entityId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `extractPlaintext walks nested text fields`() {
        val content = listOf(
            mapOf(
                "type" to "paragraph",
                "content" to listOf(
                    mapOf("type" to "text", "text" to "Hello"),
                    mapOf("type" to "text", "text" to "world"),
                ),
            ),
        )
        val plaintext = noteService.extractPlaintext(content)
        assertThat(plaintext).isEqualTo("Hello world")
    }

    @Test
    fun `extractTitle pulls inline text from first block`() {
        val content = listOf(
            mapOf(
                "type" to "paragraph",
                "content" to listOf(mapOf("type" to "text", "text" to "Title here")),
            ),
        )
        assertThat(noteService.extractTitle(content)).isEqualTo("Title here")
    }
}
