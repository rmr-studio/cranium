package cranium.core.projection.workflow

import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.entity.workflow.WorkflowNodeEntity
import cranium.core.repository.workflow.WorkflowEdgeRepository
import cranium.core.repository.workflow.WorkflowNodeRepository
import java.util.*

/**
 * Domain model representing a workflow graph with node and edge entities.
 */
data class WorkflowGraph(
    val nodes: List<WorkflowNodeEntity>,
    val edges: List<WorkflowEdgeEntity>,
)

/**
 * Fetch a complete workflow graph for the given node IDs.
 * Fetches both the nodes and all edges where source or target is in the node list.
 *
 * @param workspaceId Workspace context
 * @param nodeIds List of node IDs to include in the graph
 * @param edgeRepository Repository for fetching edges
 * @return WorkflowGraph containing node and edge entities
 */
fun WorkflowNodeRepository.fetchWorkflowGraph(
    workspaceId: UUID,
    nodeIds: List<UUID>,
    edgeRepository: WorkflowEdgeRepository,
): WorkflowGraph {
    if (nodeIds.isEmpty()) {
        return WorkflowGraph(nodes = emptyList(), edges = emptyList())
    }

    val nodes = findByWorkspaceIdAndIdIn(workspaceId, nodeIds)
    val edges = edgeRepository.findByWorkspaceIdAndNodeIds(workspaceId, nodeIds.toTypedArray())

    return WorkflowGraph(nodes = nodes, edges = edges)
}
