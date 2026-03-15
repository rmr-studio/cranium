package riven.core.repository.notification

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.notification.NotificationEntity
import riven.core.enums.notification.NotificationReferenceType
import java.time.ZonedDateTime
import java.util.UUID

interface NotificationRepository : JpaRepository<NotificationEntity, UUID> {

    @Query(
        """
        SELECT n FROM NotificationEntity n
        WHERE n.workspaceId = :workspaceId
        AND (n.userId IS NULL OR n.userId = :userId)
        AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)
        AND (n.createdAt < :cursorCreatedAt OR (n.createdAt = :cursorCreatedAt AND n.id < :cursorId))
        ORDER BY n.createdAt DESC, n.id DESC
        """
    )
    fun findInbox(
        @Param("workspaceId") workspaceId: UUID,
        @Param("userId") userId: UUID,
        @Param("cursorCreatedAt") cursorCreatedAt: ZonedDateTime,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable,
    ): List<NotificationEntity>

    @Query(
        """
        SELECT COUNT(n) FROM NotificationEntity n
        WHERE n.workspaceId = :workspaceId
        AND (n.userId IS NULL OR n.userId = :userId)
        AND (n.expiresAt IS NULL OR n.expiresAt > CURRENT_TIMESTAMP)
        AND n.id NOT IN (
            SELECT nr.notificationId FROM NotificationReadEntity nr
            WHERE nr.userId = :userId
        )
        """
    )
    fun countUnread(
        @Param("workspaceId") workspaceId: UUID,
        @Param("userId") userId: UUID,
    ): Long

    @Query(
        """
        SELECT n FROM NotificationEntity n
        WHERE n.referenceType = :referenceType
        AND n.referenceId = :referenceId
        AND n.resolved = false
        """
    )
    fun findUnresolvedByReference(
        @Param("referenceType") referenceType: NotificationReferenceType,
        @Param("referenceId") referenceId: UUID,
    ): List<NotificationEntity>

    @Query(
        """
        SELECT n FROM NotificationEntity n
        WHERE n.id = :id AND n.workspaceId = :workspaceId
        """
    )
    fun findByIdAndWorkspaceId(
        @Param("id") id: UUID,
        @Param("workspaceId") workspaceId: UUID,
    ): NotificationEntity?
}
