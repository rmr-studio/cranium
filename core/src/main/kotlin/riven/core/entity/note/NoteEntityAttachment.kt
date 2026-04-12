package riven.core.entity.note

import jakarta.persistence.Column
import jakarta.persistence.Table
import java.io.Serializable
import java.util.*
import jakarta.persistence.Entity as JPAEntity
import jakarta.persistence.Id
import jakarta.persistence.IdClass

data class NoteEntityAttachmentId(
    val noteId: UUID = UUID(0, 0),
    val entityId: UUID = UUID(0, 0),
) : Serializable

@JPAEntity
@Table(name = "note_entity_attachments")
@IdClass(NoteEntityAttachmentId::class)
data class NoteEntityAttachment(
    @Id
    @Column(name = "note_id", nullable = false)
    val noteId: UUID,

    @Id
    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,
)
