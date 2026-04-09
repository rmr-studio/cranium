package riven.core.service.util.factory.note

import riven.core.entity.note.NoteEntity
import riven.core.entity.note.NoteEntityAttachment
import riven.core.enums.note.NoteSourceType
import java.util.*

object NoteFactory {

    val DEFAULT_NOTE_ID: UUID = UUID.fromString("d1e2f3a4-b5c6-7890-abcd-ef1234567890")

    fun createEntity(
        id: UUID? = DEFAULT_NOTE_ID,
        entityId: UUID? = UUID.randomUUID(),
        workspaceId: UUID = UUID.randomUUID(),
        title: String = "Test Note",
        content: List<Map<String, Any>> = listOf(
            mapOf(
                "type" to "paragraph",
                "content" to listOf(
                    mapOf("type" to "text", "text" to "Test content")
                )
            )
        ),
        plaintext: String = "Test content",
        sourceType: NoteSourceType = NoteSourceType.USER,
        sourceIntegrationId: UUID? = null,
        sourceExternalId: String? = null,
        readonly: Boolean = false,
        pendingAssociations: Map<String, List<String>>? = null,
    ): NoteEntity = NoteEntity(
        id = id,
        entityId = entityId,
        workspaceId = workspaceId,
        title = title,
        content = content,
        plaintext = plaintext,
        sourceType = sourceType,
        sourceIntegrationId = sourceIntegrationId,
        sourceExternalId = sourceExternalId,
        readonly = readonly,
        pendingAssociations = pendingAssociations,
    )

    fun createAttachment(
        noteId: UUID = DEFAULT_NOTE_ID,
        entityId: UUID = UUID.randomUUID(),
    ): NoteEntityAttachment = NoteEntityAttachment(
        noteId = noteId,
        entityId = entityId,
    )
}
