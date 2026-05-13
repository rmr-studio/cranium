package cranium.core.repository.block

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import cranium.core.entity.block.BlockEntity
import java.util.*

interface BlockRepository : JpaRepository<BlockEntity, UUID> {
    /**
     * Retrieves all non-deleted blocks belonging to the specified workspace.
     *
     * @param workspaceId UUID of the workspace whose active (not deleted) blocks should be returned.
     * @return A list of BlockEntity instances for the workspace where `deleted` is false.
     */
    @Query("SELECT b FROM BlockEntity b WHERE b.workspaceId = :workspaceId")
    fun findByWorkspaceId(workspaceId: UUID): List<BlockEntity>
}