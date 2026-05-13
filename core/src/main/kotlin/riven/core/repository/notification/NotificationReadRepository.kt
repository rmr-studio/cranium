package cranium.core.repository.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import cranium.core.entity.notification.NotificationReadEntity
import java.util.UUID

interface NotificationReadRepository : JpaRepository<NotificationReadEntity, UUID> {

    @Query(
        """
        SELECT nr.notificationId FROM NotificationReadEntity nr
        WHERE nr.userId = :userId AND nr.notificationId IN :notificationIds
        """
    )
    fun findReadNotificationIds(
        @Param("userId") userId: UUID,
        @Param("notificationIds") notificationIds: Collection<UUID>,
    ): Set<UUID>

    @Query(
        """
        SELECT CASE WHEN COUNT(nr) > 0 THEN true ELSE false END
        FROM NotificationReadEntity nr
        WHERE nr.userId = :userId AND nr.notificationId = :notificationId
        """
    )
    fun existsByUserIdAndNotificationId(
        @Param("userId") userId: UUID,
        @Param("notificationId") notificationId: UUID,
    ): Boolean

    @Modifying
    @Query(
        value = """
            INSERT INTO notification_reads (id, user_id, notification_id, read_at)
            SELECT uuid_generate_v4(), :userId, n.id, CURRENT_TIMESTAMP
            FROM notifications n
            WHERE n.workspace_id = :workspaceId
            AND (n.user_id IS NULL OR n.user_id = :userId)
            AND (n.expires_at IS NULL OR n.expires_at > CURRENT_TIMESTAMP)
            AND n.deleted = false
            AND n.id NOT IN (
                SELECT nr.notification_id FROM notification_reads nr WHERE nr.user_id = :userId
            )
            ON CONFLICT (user_id, notification_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun markAllAsRead(
        @Param("workspaceId") workspaceId: UUID,
        @Param("userId") userId: UUID,
    )
}
