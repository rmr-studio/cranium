package riven.core.models.note

import riven.core.entity.util.AuditableModel
import riven.core.enums.note.NoteSourceType
import java.time.ZonedDateTime
import java.util.*

data class WorkspaceNote(
    val id: UUID,
    val entityIds: List<UUID>,
    val workspaceId: UUID,
    val title: String,
    val content: List<Map<String, Any>>,
    val sourceType: NoteSourceType = NoteSourceType.USER,
    val readonly: Boolean = false,
    override var createdAt: ZonedDateTime?,
    override var updatedAt: ZonedDateTime?,
    override var createdBy: UUID?,
    override var updatedBy: UUID?,
    val entityContexts: List<NoteEntityContext> = emptyList(),
) : AuditableModel

data class NoteEntityContext(
    val entityId: UUID,
    val entityDisplayName: String?,
    val entityTypeKey: String,
    val entityTypeIcon: String,
    val entityTypeColour: String,
)
