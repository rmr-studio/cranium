package riven.core.controller.note

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import riven.core.models.note.CreateNoteRequest
import riven.core.models.note.Note
import riven.core.models.note.UpdateNoteRequest
import riven.core.models.note.WorkspaceNote
import riven.core.service.note.NoteService
import riven.core.util.CursorPage
import java.util.*

@RestController
@RequestMapping("/api/v1/notes")
@Tag(name = "note")
@PreAuthorize("isAuthenticated()")
class NoteController(
    private val noteService: NoteService,
) {

    @Operation(summary = "List all notes in a workspace with cursor pagination")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workspace notes retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid cursor or limit"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @GetMapping("/workspace/{workspaceId}")
    fun getWorkspaceNotes(
        @PathVariable workspaceId: UUID,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<CursorPage<WorkspaceNote>> =
        ResponseEntity.ok(noteService.getWorkspaceNotes(workspaceId, search, cursor, limit))

    @Operation(summary = "Get a single note by ID within a workspace")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Note retrieved"),
        ApiResponse(responseCode = "404", description = "Note not found"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @GetMapping("/workspace/{workspaceId}/{noteId}")
    fun getWorkspaceNote(
        @PathVariable workspaceId: UUID,
        @PathVariable noteId: UUID,
    ): ResponseEntity<WorkspaceNote> =
        ResponseEntity.ok(noteService.getWorkspaceNote(workspaceId, noteId))

    @Operation(summary = "List notes for an entity")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Notes retrieved"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @GetMapping("/workspace/{workspaceId}/entity/{entityId}")
    fun getNotesForEntity(
        @PathVariable workspaceId: UUID,
        @PathVariable entityId: UUID,
        @RequestParam(required = false) search: String?,
    ): ResponseEntity<List<Note>> =
        ResponseEntity.ok(noteService.getNotesForEntity(workspaceId, entityId, search))

    @Operation(summary = "Create a note for an entity")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Note created"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @PostMapping("/workspace/{workspaceId}/entity/{entityId}")
    fun createNote(
        @PathVariable workspaceId: UUID,
        @PathVariable entityId: UUID,
        @Valid @RequestBody request: CreateNoteRequest,
    ): ResponseEntity<Note> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            noteService.createNote(workspaceId, entityId, request)
        )

    @Operation(summary = "Update a note")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Note updated"),
        ApiResponse(responseCode = "404", description = "Note not found"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @PutMapping("/workspace/{workspaceId}/{noteId}")
    fun updateNote(
        @PathVariable workspaceId: UUID,
        @PathVariable noteId: UUID,
        @Valid @RequestBody request: UpdateNoteRequest,
    ): ResponseEntity<Note> =
        ResponseEntity.ok(noteService.updateNote(workspaceId, noteId, request))

    @Operation(summary = "Delete a note")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Note deleted"),
        ApiResponse(responseCode = "404", description = "Note not found"),
        ApiResponse(responseCode = "403", description = "Access denied"),
    )
    @DeleteMapping("/workspace/{workspaceId}/{noteId}")
    fun deleteNote(
        @PathVariable workspaceId: UUID,
        @PathVariable noteId: UUID,
    ): ResponseEntity<Void> {
        noteService.deleteNote(workspaceId, noteId)
        return ResponseEntity.noContent().build()
    }
}
