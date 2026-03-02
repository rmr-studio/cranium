package riven.core.models.analytics

import java.time.ZonedDateTime
import java.util.UUID

data class WorkspaceCreatedEvent(
    val workspaceId: UUID,
    val workspaceName: String,
    val createdAt: ZonedDateTime?,
    val memberCount: Int,
    val userId: UUID
)

data class WorkspaceUpdatedEvent(
    val workspaceId: UUID,
    val workspaceName: String,
    val createdAt: ZonedDateTime?,
    val memberCount: Int,
    val userId: UUID
)
