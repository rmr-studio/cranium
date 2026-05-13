package cranium.core.repository.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import cranium.core.entity.user.UserEntity
import cranium.core.projection.user.UserWorkspaceMembershipProjection
import java.util.*

interface UserRepository : JpaRepository<UserEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): Optional<UserEntity>

    /**
     * Fetch all workspace memberships for a user in a single optimized query.
     * Uses native query with JOIN to avoid N+1 query problem.
     *
     * @param userId The user's UUID
     * @return List of workspace membership projections
     */
    @Query(
        value = """
            SELECT
                u.id as userId,
                u.name as userName,
                u.email as userEmail,
                u.avatar_url as userAvatarUrl,
                wm.workspace_id as workspaceId,
                w.name as workspaceName,
                w.avatar_url as workspaceAvatarUrl,
                wm.role as role,
                wm.member_since as memberSince
            FROM workspace_members wm
            INNER JOIN workspaces w ON wm.workspace_id = w.id
            INNER JOIN users u ON wm.user_id = u.id
            WHERE wm.user_id = :userId
            AND u.deleted = false
            AND w.deleted = false
            ORDER BY wm.member_since ASC
        """,
        nativeQuery = true
    )
    fun findWorkspaceMembershipsByUserId(
        @Param("userId") userId: UUID
    ): List<UserWorkspaceMembershipProjection>
}