package riven.core.service.note

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.note.NoteEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.exceptions.NotFoundException
import riven.core.models.note.CreateNoteRequest
import riven.core.models.note.Note
import riven.core.models.note.UpdateNoteRequest
import riven.core.models.note.WorkspaceNote
import riven.core.repository.note.NoteRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.util.CursorPage
import riven.core.util.CursorPagination
import java.util.*

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    // ------ Entity-scoped Read ------

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getNotesForEntity(workspaceId: UUID, entityId: UUID, search: String? = null): List<Note> {
        val entities = if (!search.isNullOrBlank()) {
            noteRepository.searchByEntityIdAndWorkspaceId(entityId, workspaceId, search)
        } else {
            noteRepository.findByEntityIdAndWorkspaceIdOrderByCreatedAtDesc(entityId, workspaceId)
        }
        return entities.map { it.toModel() }
    }

    // ------ Workspace-scoped Read ------

    /** Returns a cursor-paginated list of all notes in the workspace, enriched with entity context. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun getWorkspaceNotes(
        workspaceId: UUID,
        search: String? = null,
        cursor: String? = null,
        limit: Int = 20,
    ): CursorPage<WorkspaceNote> {
        require(limit in 1..100) { "limit must be between 1 and 100" }

        val (cursorCreatedAt, cursorId) = CursorPagination.decodeCursor(cursor)

        val notes = if (!search.isNullOrBlank()) {
            noteRepository.searchByWorkspaceId(workspaceId, search, cursorCreatedAt, cursorId, limit)
        } else {
            noteRepository.findByWorkspaceId(workspaceId, cursorCreatedAt, cursorId, PageRequest.of(0, limit))
        }

        val totalCount = noteRepository.countByWorkspaceId(workspaceId)
        val enriched = enrichWithEntityContext(notes)

        val nextCursor = if (notes.size == limit) {
            val last = notes.last()
            CursorPagination.encodeCursor(
                requireNotNull(last.createdAt) { "createdAt must not be null for cursor encoding" },
                requireNotNull(last.id) { "id must not be null for cursor encoding" },
            )
        } else null

        return CursorPage(
            items = enriched,
            nextCursor = nextCursor,
            totalCount = totalCount,
        )
    }

    /** Returns a single workspace note enriched with entity context. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun getWorkspaceNote(workspaceId: UUID, noteId: UUID): WorkspaceNote {
        val entity = noteRepository.findByIdAndWorkspaceId(noteId, workspaceId)
            ?: throw NotFoundException("Note not found: $noteId")

        return enrichWithEntityContext(listOf(entity)).first()
    }

    // ------ Create ------

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun createNote(workspaceId: UUID, entityId: UUID, request: CreateNoteRequest): Note {
        val userId = authTokenService.getUserId()
        val plaintext = extractPlaintext(request.content)
        val title = request.title ?: extractTitle(request.content)

        val entity = NoteEntity(
            entityId = entityId,
            workspaceId = workspaceId,
            title = title,
            content = request.content,
            plaintext = plaintext,
        )

        val saved = noteRepository.save(entity)
        val note = saved.toModel()

        activityService.log(
            activity = Activity.NOTE,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTE,
            entityId = note.id,
            "entityId" to entityId.toString(),
            "title" to title,
        )

        logger.info { "Note created: ${note.id} for entity $entityId in workspace $workspaceId" }
        return note
    }

    // ------ Update ------

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun updateNote(workspaceId: UUID, noteId: UUID, request: UpdateNoteRequest): Note {
        val userId = authTokenService.getUserId()
        val entity = noteRepository.findById(noteId)
            .orElseThrow { NotFoundException("Note not found: $noteId") }

        require(entity.workspaceId == workspaceId) { "Note does not belong to workspace $workspaceId" }

        if (request.content != null) {
            entity.content = request.content
            entity.plaintext = extractPlaintext(request.content)
            entity.title = request.title ?: extractTitle(request.content)
        } else if (request.title != null) {
            entity.title = request.title
        }

        val saved = noteRepository.save(entity)
        val note = saved.toModel()

        activityService.log(
            activity = Activity.NOTE,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTE,
            entityId = note.id,
            "entityId" to entity.entityId.toString(),
            "title" to note.title,
        )

        logger.info { "Note updated: ${note.id}" }
        return note
    }

    // ------ Delete ------

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteNote(workspaceId: UUID, noteId: UUID) {
        val userId = authTokenService.getUserId()
        val entity = noteRepository.findById(noteId)
            .orElseThrow { NotFoundException("Note not found: $noteId") }

        require(entity.workspaceId == workspaceId) { "Note does not belong to workspace $workspaceId" }

        noteRepository.delete(entity)

        activityService.log(
            activity = Activity.NOTE,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTE,
            entityId = noteId,
            "entityId" to entity.entityId.toString(),
            "title" to entity.title,
        )

        logger.info { "Note deleted: $noteId" }
    }

    // ------ Private helpers ------

    /**
     * Batch-fetches entity context (display name, type key, icon) and maps note entities
     * to enriched WorkspaceNote models.
     *
     * Entity context is resolved via a single native SQL query that JOINs entities → entity_types
     * and sub-SELECTs the identifier attribute value from entity_attributes for the display name.
     */
    private fun enrichWithEntityContext(notes: List<NoteEntity>): List<WorkspaceNote> {
        if (notes.isEmpty()) return emptyList()

        val entityIds = notes.map { it.entityId }.distinct()
        val contextRows = noteRepository.findEntityContext(entityIds)

        // Map entity_id → (displayName, typeKey, iconType, iconColour)
        val contextMap = contextRows.associate { row ->
            val entityId = row[0] as UUID
            entityId to EntityContext(
                displayName = row[1] as? String,
                typeKey = row[2] as String,
                iconType = row[3] as String,
                iconColour = row[4] as String,
            )
        }

        return notes.map { note ->
            val context = contextMap[note.entityId]
            WorkspaceNote(
                id = requireNotNull(note.id) { "Note ID must not be null" },
                entityId = note.entityId,
                workspaceId = note.workspaceId,
                title = note.title,
                content = note.content,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                createdBy = note.createdBy,
                updatedBy = note.updatedBy,
                entityDisplayName = context?.displayName,
                entityTypeKey = context?.typeKey ?: "",
                entityTypeIcon = context?.iconType ?: "",
                entityTypeColour = context?.iconColour ?: "",
            )
        }
    }

    private data class EntityContext(
        val displayName: String?,
        val typeKey: String,
        val iconType: String,
        val iconColour: String,
    )

    /**
     * Recursively extracts all string values from "text" keys in the JSONB content tree.
     * Format-agnostic — works with any nested JSON structure containing text fields.
     */
    private fun extractPlaintext(content: List<Map<String, Any>>): String {
        val texts = mutableListOf<String>()
        fun walk(obj: Any?) {
            when (obj) {
                is Map<*, *> -> {
                    val textValue = obj["text"]
                    if (textValue is String) texts.add(textValue)
                    obj.values.forEach { walk(it) }
                }
                is List<*> -> obj.forEach { walk(it) }
            }
        }
        walk(content)
        return texts.joinToString(" ").trim()
    }

    /**
     * Extracts the title from the first text block's inline content.
     * Returns the first 255 characters of concatenated text.
     */
    private fun extractTitle(content: List<Map<String, Any>>): String {
        if (content.isEmpty()) return ""
        val firstBlock = content[0]
        val inlineContent = firstBlock["content"]
        if (inlineContent !is List<*>) return ""

        val title = inlineContent
            .filterIsInstance<Map<*, *>>()
            .mapNotNull { item ->
                when (item["type"]) {
                    "text" -> item["text"] as? String
                    "link" -> (item["content"] as? List<*>)
                        ?.filterIsInstance<Map<*, *>>()
                        ?.mapNotNull { it["text"] as? String }
                        ?.joinToString("")
                    else -> null
                }
            }
            .joinToString("")

        return title.take(255)
    }
}
