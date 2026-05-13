package cranium.core.repository.workflow

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import cranium.core.entity.workflow.WorkflowNodeEntity
import java.util.UUID

/**
 * Repository for accessing WorkflowNodeEntity instances.
 *
 * Provides standard CRUD operations and custom queries for workflow nodes.
 */
@Repository
interface WorkflowNodeRepository : JpaRepository<WorkflowNodeEntity, UUID> {

    /**
     * Find a workflow node by workspace and key.
     *
     * @param workspaceId Workspace context
     * @param key Node key identifier
     * @return The workflow node entity if found
     */
    fun findByWorkspaceIdAndKey(workspaceId: UUID, key: String): WorkflowNodeEntity?

    /**
     * Find all workflow nodes for a workspace.
     *
     * @param workspaceId Workspace context
     * @return List of workflow nodes in the workspace
     */
    fun findByWorkspaceId(workspaceId: UUID): List<WorkflowNodeEntity>

    /**
     * Find workflow nodes by workspace and list of node IDs.
     * Only returns non-deleted nodes.
     *
     * @param workspaceId Workspace context
     * @param nodeIds List of node IDs to fetch
     * @return List of workflow node entities
     */
    @Query(
        """
        SELECT n FROM WorkflowNodeEntity n
        WHERE n.workspaceId = :workspaceId
        AND n.id IN :nodeIds
        """
    )
    fun findByWorkspaceIdAndIdIn(workspaceId: UUID, nodeIds: Collection<UUID>): List<WorkflowNodeEntity>

    /**
     * Find a workflow node by workspace and ID.
     *
     * @param workspaceId Workspace context
     * @param id Node ID
     * @return The workflow node entity if found
     */
    @Query(
        """
        SELECT n FROM WorkflowNodeEntity n
        WHERE n.workspaceId = :workspaceId
        AND n.id = :id
        """
    )
    fun findByWorkspaceIdAndId(workspaceId: UUID, id: UUID): WorkflowNodeEntity?
}
