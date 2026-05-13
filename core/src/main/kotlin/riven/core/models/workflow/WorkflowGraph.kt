package cranium.core.models.workflow

import cranium.core.models.workflow.node.WorkflowNode
import java.util.UUID

/**
 * Complete workflow graph containing nodes and edges.
 *
 * Represents the DAG structure of a workflow definition,
 * including all nodes and their connections (edges).
 *
 * @property workflowDefinitionId The workflow definition this graph belongs to
 * @property nodes All nodes in the workflow graph
 * @property edges All edges connecting the nodes
 */
data class WorkflowGraph(
    val workflowDefinitionId: UUID,
    val nodes: List<WorkflowNode>,
    val edges: List<WorkflowEdge>
) {
    /**
     * The root node of the workflow graph.
     *
     * Computed by finding the node with no incoming edges (not a target of any edge).
     * In a valid DAG, there should be exactly one such node.
     *
     * @throws NoSuchElementException if no root node is found
     */
    val root: WorkflowNode by lazy {
        val targetNodeIds = edges.map { it.target.id }.toSet()
        nodes.first { it.id !in targetNodeIds }
    }
}
