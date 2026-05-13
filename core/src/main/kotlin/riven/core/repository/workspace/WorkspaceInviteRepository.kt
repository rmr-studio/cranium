package cranium.core.repository.workspace

import org.springframework.data.jpa.repository.JpaRepository
import cranium.core.entity.workspace.WorkspaceInviteEntity
import cranium.core.enums.workspace.WorkspaceInviteStatus
import java.util.*


interface WorkspaceInviteRepository : JpaRepository<WorkspaceInviteEntity, UUID> {
    fun findByworkspaceId(id: UUID): List<WorkspaceInviteEntity>
    fun findByEmail(email: String): List<WorkspaceInviteEntity>
    fun findByworkspaceIdAndEmailAndInviteStatus(
        workspaceId: UUID,
        email: String,
        inviteStatus: WorkspaceInviteStatus
    ): List<WorkspaceInviteEntity>

    fun findByToken(token: String): Optional<WorkspaceInviteEntity>
}