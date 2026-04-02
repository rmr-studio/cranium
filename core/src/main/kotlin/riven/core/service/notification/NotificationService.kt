package riven.core.service.notification

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.notification.NotificationEntity
import riven.core.entity.notification.NotificationReadEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.notification.NotificationReferenceType
import riven.core.enums.notification.NotificationType
import riven.core.enums.util.OperationType
import org.springframework.dao.DataIntegrityViolationException
import riven.core.exceptions.NotFoundException
import riven.core.models.common.markDeleted
import riven.core.models.notification.NotificationInboxItem
import riven.core.models.notification.Notification
import riven.core.models.request.notification.CreateNotificationRequest
import riven.core.models.response.notification.NotificationInboxResponse
import riven.core.models.websocket.NotificationEvent
import riven.core.repository.notification.NotificationReadRepository
import riven.core.repository.notification.NotificationRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.util.CursorPagination
import java.time.ZonedDateTime
import java.util.UUID

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val notificationReadRepository: NotificationReadRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val logger: KLogger,
) {

    companion object {
        /** Notification types that can be created via the public REST API. */
        private val PUBLIC_NOTIFICATION_TYPES = setOf(NotificationType.INFORMATION)
    }

    // ------ Create ------

    /**
     * Creates a notification from the public REST API.
     *
     * Restricts allowed types to [PUBLIC_NOTIFICATION_TYPES] — privileged types
     * (SYSTEM, REVIEW_REQUEST) must be created via [createInternalNotification].
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#request.workspaceId)")
    @Transactional
    fun createNotification(request: CreateNotificationRequest): Notification {
        require(request.type in PUBLIC_NOTIFICATION_TYPES) {
            "Notification type ${request.type} cannot be created via the public API"
        }
        val userId = authTokenService.getUserId()
        return persistAndPublishNotification(request, userId)
    }

    /**
     * Creates a notification from internal services (e.g. domain event handlers).
     *
     * No type restriction — all [NotificationType] values are allowed. No workspace
     * auth check because internal callers operate across workspaces by design.
     */
    @Transactional
    fun createInternalNotification(request: CreateNotificationRequest): Notification {
        val userId = authTokenService.getUserId()
        return persistAndPublishNotification(request, userId)
    }

    private fun persistAndPublishNotification(request: CreateNotificationRequest, userId: UUID): Notification {
        val entity = NotificationEntity(
            workspaceId = request.workspaceId,
            userId = request.userId,
            type = request.type,
            content = request.content,
            referenceType = request.referenceType,
            referenceId = request.referenceId,
            expiresAt = request.expiresAt,
        )

        val saved = notificationRepository.save(entity)

        logCreationActivity(userId, saved)
        publishNotificationEvent(saved, OperationType.CREATE, userId)

        logger.info { "Notification created: type=${saved.type} workspace=${saved.workspaceId} target=${saved.userId ?: "all"}" }

        return saved.toModel()
    }

    // ------ Read Operations ------

    /** Returns a cursor-paginated inbox for the current user within a workspace. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional(readOnly = true)
    fun getInbox(workspaceId: UUID, cursor: String?, pageSize: Int): NotificationInboxResponse {
        require(pageSize in 1..100) { "pageSize must be between 1 and 100" }

        val userId = authTokenService.getUserId()
        val (cursorCreatedAt, cursorId) = CursorPagination.decodeCursor(cursor)

        val notifications = notificationRepository.findInbox(
            workspaceId = workspaceId,
            userId = userId,
            cursorCreatedAt = cursorCreatedAt,
            cursorId = cursorId,
            pageable = PageRequest.of(0, pageSize),
        )

        val readIds = fetchReadIds(userId, notifications)

        val items = notifications.map { it.toInboxItem(isRead = it.id in readIds) }
        val nextCursor = if (items.size == pageSize) {
            val last = notifications.last()
            CursorPagination.encodeCursor(
                requireNotNull(last.createdAt) { "createdAt must not be null for cursor encoding" },
                requireNotNull(last.id) { "id must not be null for cursor encoding" },
            )
        } else null
        val unreadCount = countUnread(workspaceId, userId)

        return NotificationInboxResponse(
            notifications = items,
            nextCursor = nextCursor,
            unreadCount = unreadCount,
        )
    }

    /** Returns the number of unread notifications for the current user's inbox. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getUnreadCount(workspaceId: UUID): Long {
        val userId = authTokenService.getUserId()
        return countUnread(workspaceId, userId)
    }

    // ------ Read State Mutations ------

    /** Marks a single notification as read for the current user. Idempotent. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun markAsRead(workspaceId: UUID, notificationId: UUID) {
        val userId = authTokenService.getUserId()

        notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId)
            ?: throw NotFoundException("Notification not found: $notificationId")

        try {
            notificationReadRepository.save(
                NotificationReadEntity(userId = userId, notificationId = notificationId)
            )
        } catch (_: DataIntegrityViolationException) {
            // Duplicate read entry — idempotent, silently ignored
        }
    }

    /** Marks all visible notifications in the workspace as read for the current user. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun markAllAsRead(workspaceId: UUID) {
        val userId = authTokenService.getUserId()
        notificationReadRepository.markAllAsRead(workspaceId, userId)
    }

    // ------ Resolution ------

    /**
     * Marks all unresolved notifications referencing the given entity as resolved.
     *
     * This is an internal method called by domain event handlers when the referenced
     * action has been completed. No @PreAuthorize is applied because this is not
     * exposed via REST -- it operates across workspaces by design (a single domain
     * event may resolve notifications in any workspace).
     */
    @Transactional
    fun resolveByReference(referenceType: NotificationReferenceType, referenceId: UUID) {
        val unresolvedNotifications = notificationRepository.findUnresolvedByReference(referenceType, referenceId)

        if (unresolvedNotifications.isEmpty()) {
            logger.debug { "No unresolved notifications for $referenceType:$referenceId" }
            return
        }

        val now = ZonedDateTime.now()
        unresolvedNotifications.forEach { notification ->
            notification.resolved = true
            notification.resolvedAt = now
        }

        notificationRepository.saveAll(unresolvedNotifications)
        logger.info { "Resolved ${unresolvedNotifications.size} notifications for $referenceType:$referenceId" }
    }

    // ------ Delete ------

    /** Soft-deletes a notification. Logs activity for audit trail. */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun deleteNotification(workspaceId: UUID, notificationId: UUID) {
        val userId = authTokenService.getUserId()

        val notification = notificationRepository.findByIdAndWorkspaceId(notificationId, workspaceId)
            ?: throw NotFoundException("Notification not found: $notificationId")

        notification.markDeleted()
        notificationRepository.save(notification)

        activityService.log(
            activity = Activity.NOTIFICATION,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.NOTIFICATION,
            entityId = notificationId,
            "type" to notification.type.name,
        )
    }

    // ------ Private Helpers ------

    private fun logCreationActivity(userId: UUID, entity: NotificationEntity) {
        val notificationId = requireNotNull(entity.id)
        activityService.log(
            activity = Activity.NOTIFICATION,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = entity.workspaceId,
            entityType = ApplicationEntityType.NOTIFICATION,
            entityId = notificationId,
            "type" to entity.type.name,
            "targetUserId" to entity.userId?.toString(),
            "referenceType" to entity.referenceType?.name,
            "referenceId" to entity.referenceId?.toString(),
        )
    }

    private fun publishNotificationEvent(entity: NotificationEntity, operation: OperationType, userId: UUID) {
        val notificationId = requireNotNull(entity.id)
        applicationEventPublisher.publishEvent(
            NotificationEvent(
                workspaceId = entity.workspaceId,
                userId = userId,
                operation = operation,
                entityId = notificationId,
                notificationType = entity.type,
                summary = mapOf(
                    "title" to entity.content.title,
                    "message" to entity.content.message,
                    "referenceType" to entity.referenceType?.name,
                    "referenceId" to entity.referenceId?.toString(),
                    "userDisplayName" to authTokenService.getUserDisplayName(),
                ),
            )
        )
    }

    private fun countUnread(workspaceId: UUID, userId: UUID): Long =
        notificationRepository.countUnread(workspaceId, userId)

    private fun fetchReadIds(userId: UUID, notifications: List<NotificationEntity>): Set<UUID> =
        if (notifications.isNotEmpty()) {
            notificationReadRepository.findReadNotificationIds(
                userId = userId,
                notificationIds = notifications.mapNotNull { it.id },
            )
        } else {
            emptySet()
        }

    private fun NotificationEntity.toInboxItem(isRead: Boolean): NotificationInboxItem =
        NotificationInboxItem(
            id = requireNotNull(this.id),
            type = this.type,
            content = this.content,
            referenceType = this.referenceType,
            referenceId = this.referenceId,
            resolved = this.resolved,
            read = isRead,
            createdAt = requireNotNull(this.createdAt),
        )
}
