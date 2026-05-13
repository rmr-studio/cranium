package cranium.core.controller.note

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import cranium.core.configuration.properties.ApplicationConfigurationProperties
import cranium.core.enums.note.NoteSourceType
import cranium.core.exceptions.ExceptionHandler
import cranium.core.models.note.Note
import cranium.core.models.note.NoteEntityContext
import cranium.core.models.note.WorkspaceNote
import cranium.core.service.note.NoteService
import cranium.core.util.CursorPage
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

/**
 * E2E regression — controller contract preserved under the entity-backed reads + writes
 * post-cutover. Standalone MockMvc against the [NoteController]; the [NoteService] is
 * mocked at the boundary so this test exercises only the wire format.
 *
 * Asserts every endpoint returns the same Note / WorkspaceNote JSON shape the frontend
 * has historically consumed (id, entityIds, workspaceId, title, content, sourceType,
 * readonly, createdAt, updatedAt, createdBy, updatedBy, entityContexts).
 *
 * Per the existing controller-test convention (see DataConnectorMappingControllerTest),
 * @PreAuthorize is not exercised under standalone MockMvc — auth is covered at the
 * service layer (NoteServiceTest).
 */
class NoteControllerE2EIT {

    private val noteService: NoteService = mock(NoteService::class.java)
    private val logger: KLogger = mock(KLogger::class.java)

    private val controller = NoteController(noteService)

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val config = ApplicationConfigurationProperties(
        includeStackTrace = false,
        supabaseUrl = "http://test",
        supabaseKey = "test",
    )
    private val advice = ExceptionHandler(logger, config)

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setControllerAdvice(advice)
        .build()

    private val workspaceId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val noteId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val entityId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @BeforeEach
    fun setup() {
        reset(noteService)
    }

    private fun sampleNote(): Note = Note(
        id = noteId,
        entityIds = listOf(entityId),
        workspaceId = workspaceId,
        title = "Status Update",
        content = listOf(
            mapOf(
                "type" to "paragraph",
                "content" to listOf(mapOf("type" to "text", "text" to "Hello world")),
            ),
        ),
        sourceType = NoteSourceType.USER,
        readonly = false,
        createdAt = null,
        updatedAt = null,
        createdBy = null,
        updatedBy = null,
    )

    private fun sampleWorkspaceNote(): WorkspaceNote = WorkspaceNote(
        id = noteId,
        entityIds = listOf(entityId),
        workspaceId = workspaceId,
        title = "Status Update",
        content = listOf(mapOf("type" to "paragraph")),
        sourceType = NoteSourceType.USER,
        readonly = false,
        createdAt = null,
        updatedAt = null,
        createdBy = null,
        updatedBy = null,
        entityContexts = listOf(
            NoteEntityContext(
                entityId = entityId,
                entityDisplayName = "Acme Corp",
                entityTypeKey = "company",
                entityTypeIcon = "BUILDING",
                entityTypeColour = "BLUE",
            ),
        ),
    )

    @Test
    fun getWorkspaceNotes_returnsCursorPageWithEntityContexts() {
        whenever(noteService.getWorkspaceNotes(workspaceId, null, null, 20))
            .thenReturn(CursorPage(items = listOf(sampleWorkspaceNote()), nextCursor = null, totalCount = 1L))

        mockMvc.perform(get("/api/v1/notes/workspace/{workspaceId}", workspaceId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(noteId.toString()))
            .andExpect(jsonPath("$.items[0].title").value("Status Update"))
            .andExpect(jsonPath("$.items[0].entityIds[0]").value(entityId.toString()))
            .andExpect(jsonPath("$.items[0].sourceType").value("USER"))
            .andExpect(jsonPath("$.items[0].readonly").value(false))
            .andExpect(jsonPath("$.items[0].entityContexts[0].entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.items[0].entityContexts[0].entityDisplayName").value("Acme Corp"))
            .andExpect(jsonPath("$.items[0].entityContexts[0].entityTypeKey").value("company"))
            .andExpect(jsonPath("$.totalCount").value(1))
    }

    @Test
    fun getWorkspaceNote_returnsSingleWorkspaceNote() {
        whenever(noteService.getWorkspaceNote(workspaceId, noteId)).thenReturn(sampleWorkspaceNote())

        mockMvc.perform(get("/api/v1/notes/workspace/{workspaceId}/{noteId}", workspaceId, noteId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(noteId.toString()))
            .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
            .andExpect(jsonPath("$.title").value("Status Update"))
    }

    @Test
    fun getNotesForEntity_returnsListOfNotes() {
        whenever(noteService.getNotesForEntity(workspaceId, entityId, null))
            .thenReturn(listOf(sampleNote()))

        mockMvc.perform(get("/api/v1/notes/workspace/{workspaceId}/entity/{entityId}", workspaceId, entityId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(noteId.toString()))
            .andExpect(jsonPath("$[0].entityIds[0]").value(entityId.toString()))
            .andExpect(jsonPath("$[0].title").value("Status Update"))
    }

    @Test
    fun createNote_returns201WithCreatedNote() {
        whenever(noteService.createNote(eq(workspaceId), eq(entityId), any())).thenReturn(sampleNote())

        val body = objectMapper.writeValueAsString(
            mapOf(
                "title" to "Status Update",
                "content" to listOf(
                    mapOf(
                        "type" to "paragraph",
                        "content" to listOf(mapOf("type" to "text", "text" to "Hello world")),
                    ),
                ),
            ),
        )

        mockMvc.perform(
            post("/api/v1/notes/workspace/{workspaceId}/entity/{entityId}", workspaceId, entityId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(noteId.toString()))
            .andExpect(jsonPath("$.title").value("Status Update"))
    }

    @Test
    fun updateNote_returns200WithUpdatedNote() {
        whenever(noteService.updateNote(eq(workspaceId), eq(noteId), any())).thenReturn(sampleNote())

        val body = objectMapper.writeValueAsString(mapOf("title" to "Status Update"))

        mockMvc.perform(
            put("/api/v1/notes/workspace/{workspaceId}/{noteId}", workspaceId, noteId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(noteId.toString()))
            .andExpect(jsonPath("$.title").value("Status Update"))
    }

    @Test
    fun deleteNote_returns204NoContent() {
        mockMvc.perform(delete("/api/v1/notes/workspace/{workspaceId}/{noteId}", workspaceId, noteId))
            .andExpect(status().isNoContent)

        verify(noteService).deleteNote(workspaceId, noteId)
    }
}
