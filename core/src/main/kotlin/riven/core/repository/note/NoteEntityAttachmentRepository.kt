package riven.core.repository.note

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.note.NoteEntityAttachment
import riven.core.entity.note.NoteEntityAttachmentId
import java.util.*

interface NoteEntityAttachmentRepository : JpaRepository<NoteEntityAttachment, NoteEntityAttachmentId> {

    fun findByNoteId(noteId: UUID): List<NoteEntityAttachment>

    fun findByEntityId(entityId: UUID): List<NoteEntityAttachment>

    @Modifying
    @Query("DELETE FROM NoteEntityAttachment a WHERE a.noteId = :noteId")
    fun deleteByNoteId(@Param("noteId") noteId: UUID)

    @Query("SELECT a.entityId FROM NoteEntityAttachment a WHERE a.noteId = :noteId")
    fun findEntityIdsByNoteId(@Param("noteId") noteId: UUID): List<UUID>
}
