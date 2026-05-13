package cranium.core.repository.workflow

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import cranium.core.entity.workflow.WorkflowEdgeEntity
import java.util.UUID

interface WorkflowEdgeRepository : JpaRepository<WorkflowEdgeEntity, UUID> {

    /**
     * Find all edges where the source or target node is in the provided list.
     * This enables fetching the complete graph connectivity for a set of nodes.
     *
     * @param workspaceId Workspace context
     * @param nodeIds List of node IDs to find connected edges for
     * @return List of edge entities where source or target is in the node list
     */
    @Query(
        value = """
            SELECT e.* FROM workflow_edges e
            WHERE e.workspace_id = :workspaceId
            AND e.deleted = false
            AND (e.source_node_id = ANY(:nodeIds) OR e.target_node_id = ANY(:nodeIds))
        """,
        nativeQuery = true
    )
    fun findByWorkspaceIdAndNodeIds(workspaceId: UUID, nodeIds: Array<UUID>): List<WorkflowEdgeEntity>

    /**
     * Find all non-deleted edges connected to a specific node (as source or target).
     * Used for cascade deletion when a node is deleted.
     *
     * @param workspaceId Workspace context
     * @param nodeId The node ID to find connected edges for
     * @return List of edge entities where source or target matches the node
     */
    @Query(
        """
        SELECT e FROM WorkflowEdgeEntity e
        WHERE e.workspaceId = :workspaceId
        AND (e.sourceNodeId = :nodeId OR e.targetNodeId = :nodeId)
        """
    )
    fun findByWorkspaceIdAndNodeId(workspaceId: UUID, nodeId: UUID): List<WorkflowEdgeEntity>

    /**
     * Bulk update edges to migrate source node references from old ID to new ID.
     * Used when a node is versioned (immutable pattern) to keep edges pointing to current version.
     *
     * @param oldId The old node ID being replaced
     * @param newId The new node ID to reference
     * @return Number of edges updated
     */
    @Modifying
    @Query(
        """
        UPDATE WorkflowEdgeEntity e
        SET e.sourceNodeId = :newId
        WHERE e.sourceNodeId = :oldId AND e.deleted = false
        """
    )
    fun updateSourceNodeId(oldId: UUID, newId: UUID): Int

    /**
     * Bulk update edges to migrate target node references from old ID to new ID.
     * Used when a node is versioned (immutable pattern) to keep edges pointing to current version.
     *
     * @param oldId The old node ID being replaced
     * @param newId The new node ID to reference
     * @return Number of edges updated
     */
    @Modifying
    @Query(
        """
        UPDATE WorkflowEdgeEntity e
        SET e.targetNodeId = :newId
        WHERE e.targetNodeId = :oldId AND e.deleted = false
        """
    )
    fun updateTargetNodeId(oldId: UUID, newId: UUID): Int
}