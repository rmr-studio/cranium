package cranium.core.models.websocket

import cranium.core.enums.notification.NotificationType
import cranium.core.enums.util.OperationType
import cranium.core.enums.websocket.WebSocketChannel
import java.util.UUID

/**
 * Base interface for all workspace-scoped domain events that should be
 * broadcast over WebSocket. Published via ApplicationEventPublisher inside
 * @Transactional service methods and consumed by WebSocketEventListener
 * after transaction commit.
 */
sealed interface WorkspaceEvent {
    val workspaceId: UUID
    val userId: UUID
    val operation: OperationType
    val channel: WebSocketChannel
    val entityId: UUID?
    /** Feed-relevant fields for in-place list updates. */
    val summary: Map<String, Any?>
}

/**
 * Published when an entity instance is created, updated, or deleted.
 */
data class EntityEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    val entityTypeId: UUID,
    val entityTypeKey: String,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.ENTITIES
}

/**
 * Published when a block environment is saved (structural changes to the block tree).
 */
data class BlockEnvironmentEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    val layoutId: UUID,
    val version: Int,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.BLOCKS
}

/**
 * Published when a workflow definition or execution state changes.
 */
data class WorkflowEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.WORKFLOWS
}

/**
 * Published when workspace-level properties change (name, settings, membership).
 */
data class WorkspaceChangeEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID? = null,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.WORKSPACE
}

/**
 * Published when a notification is created, resolved, or deleted.
 * The existing WebSocketEventListener broadcasts this to the NOTIFICATIONS channel.
 */
data class NotificationEvent(
    override val workspaceId: UUID,
    override val userId: UUID,
    override val operation: OperationType,
    override val entityId: UUID?,
    val notificationType: NotificationType,
    override val summary: Map<String, Any?> = emptyMap(),
) : WorkspaceEvent {
    override val channel: WebSocketChannel = WebSocketChannel.NOTIFICATIONS
}
