package riven.core.models.note

import riven.core.entity.util.AuditableModel
import java.time.ZonedDateTime
import java.util.*

data class WorkspaceNote(
    val id: UUID,
    val entityId: UUID,
    val workspaceId: UUID,
    val title: String,
    val content: List<Map<String, Any>>,
    override var createdAt: ZonedDateTime?,
    override var updatedAt: ZonedDateTime?,
    override var createdBy: UUID?,
    override var updatedBy: UUID?,
    val entityDisplayName: String?,
    val entityTypeKey: String,
    val entityTypeIcon: String,
    val entityTypeColour: String,
) : AuditableModel
