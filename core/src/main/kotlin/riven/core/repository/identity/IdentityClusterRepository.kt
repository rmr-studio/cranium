package riven.core.repository.identity

import org.springframework.data.jpa.repository.JpaRepository
import riven.core.entity.identity.IdentityClusterEntity
import java.util.Optional
import java.util.UUID

/**
 * Repository for IdentityClusterEntity instances.
 */
interface IdentityClusterRepository : JpaRepository<IdentityClusterEntity, UUID> {

    /**
     * Returns all clusters belonging to the given workspace.
     */
    fun findByWorkspaceId(workspaceId: UUID): List<IdentityClusterEntity>

    /**
     * Returns a single cluster by ID scoped to the given workspace, or empty if not found.
     *
     * Used to enforce workspace isolation when loading cluster detail.
     */
    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<IdentityClusterEntity>
}
