package cranium.core.service.note

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.entity.EntityEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.integration.SourceType
import cranium.core.enums.knowledge.KnowledgeEntityTypeKey
import cranium.core.enums.util.OperationType
import cranium.core.exceptions.NotFoundException
import cranium.core.models.note.CreateNoteRequest
import cranium.core.models.note.Note
import cranium.core.models.note.UpdateNoteRequest
import cranium.core.models.note.WorkspaceNote
import cranium.core.service.activity.ActivityService
import cranium.core.service.activity.log
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.entity.EntityIngestionService
import cranium.core.util.CursorPage
import java.util.UUID

/**
 * Public note API. Post-cutover, every read and write path is backed by the entity layer:
 *   - mutations route through [NoteEntityIngestionService] (`upsert` / `softDelete`);
 *   - reads route through [NoteEntityProjector], which reshapes entity rows back into
 *     the existing `Note` / `WorkspaceNote` DTO contract.
 *
 * The legacy `notes` and `note_entity_attachments` JPA scaffolding is no longer
 * referenced from this service — Phase F deletes the table + entity files.
 */
@Service
class NoteService(
    private val entityIngestionService: EntityIngestionService,
    private val noteEntityIngestionService: NoteEntityIngestionService,
    private val noteEntityProjector: NoteEntityProjector,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val logger: KLogger,
) {

    // ------ Entity-scoped Read ------

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun getNotesForEntity(workspaceId: UUID, entityId: UUID, search: String? = null): List<Note> =
        noteEntityProjector.getNotesForEntity(workspaceId, entityId, search)

    // ------ Workspace-scoped Read ------

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun getWorkspaceNotes(
        workspaceId: UUID,
        search: String? = null,
        cursor: String? = null,
        limit: Int = 20,
    ): CursorPage<WorkspaceNote> = noteEntityProjector.listNotes(workspaceId, search, cursor, limit)

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun getWorkspaceNote(workspaceId: UUID, noteId: UUID): WorkspaceNote {
        val entity = entityIngestionService.findByIdInternal(workspaceId, noteId)
            ?: throw NotFoundException("Note not found: $noteId")
        if (entity.typeKey != KnowledgeEntityTypeKey.NOTE.key) {
            throw NotFoundException("Note not found: $noteId")
        }
        return noteEntityProjector.projectWorkspaceNote(workspaceId, entity)
    }

    // ------ Create ------

    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun createNote(workspaceId: UUID, entityId: UUID, request: CreateNoteRequest): Note {
        val userId = authTokenService.getUserId()
        val plaintext = extractPlaintext(request.content)
        val title = request.title ?: extractTitle(request.content)

        val saved = noteEntityIngestionService.upsert(
            NoteEntityIngestionService.NoteIngestionInput(
                workspaceId = workspaceId,
                title = title,
                content = request.content,
                plaintext = plaintext,
                targetEntityIds = setOf(entityId),
                sourceType = SourceType.USER_CREATED,
                linkSource = SourceType.USER_CREATED,
            ),
        )
        val note = noteEntityProjector.projectNote(workspaceId, saved)

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
        val existing = requireNoteEntity(workspaceId, noteId)

        if (existing.sourceType == SourceType.INTEGRATION) {
            throw AccessDeniedException("Cannot modify readonly note")
        }

        val currentNote = noteEntityProjector.projectNote(workspaceId, existing)
        val newContent = request.content ?: currentNote.content
        val newTitle = request.title ?: currentNote.title
        val newPlaintext = if (request.content != null) extractPlaintext(newContent) else extractPlaintext(currentNote.content)
        val targetEntityIds = currentNote.entityIds.toSet()

        val saved = noteEntityIngestionService.upsert(
            NoteEntityIngestionService.NoteIngestionInput(
                workspaceId = workspaceId,
                title = newTitle,
                content = newContent,
                plaintext = newPlaintext,
                targetEntityIds = targetEntityIds,
                sourceType = SourceType.USER_CREATED,
                sourceIntegrationId = existing.sourceIntegrationId,
                sourceExternalId = existing.sourceExternalId,
                linkSource = SourceType.USER_CREATED,
                existingId = noteId,
            ),
        )
        val note = noteEntityProjector.projectNote(workspaceId, saved)

        activityService.log(
            activity = Activity.NOTE,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTE,
            entityId = note.id,
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
        val existing = requireNoteEntity(workspaceId, noteId)

        if (existing.sourceType == SourceType.INTEGRATION) {
            throw AccessDeniedException("Cannot modify readonly note")
        }

        noteEntityIngestionService.softDelete(workspaceId, noteId)

        activityService.log(
            activity = Activity.NOTE,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTE,
            entityId = noteId,
            "title" to (existing.let { noteEntityProjector.projectNote(workspaceId, it).title }),
        )

        logger.info { "Note deleted: $noteId" }
    }

    // ------ Private helpers ------

    private fun requireNoteEntity(workspaceId: UUID, noteId: UUID): EntityEntity {
        val entity = entityIngestionService.findByIdInternal(workspaceId, noteId)
            ?: throw NotFoundException("Note not found: $noteId")
        if (entity.typeKey != KnowledgeEntityTypeKey.NOTE.key) {
            throw NotFoundException("Note not found: $noteId")
        }
        return entity
    }

    /**
     * Recursively extracts all string values from "text" keys in the JSONB content tree.
     * Format-agnostic — works with any nested JSON structure containing text fields.
     */
    internal fun extractPlaintext(content: List<Map<String, Any>>): String {
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
    internal fun extractTitle(content: List<Map<String, Any>>): String {
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
