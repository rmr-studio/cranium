package cranium.core.service.workflow.engine.coordinator

import org.springframework.stereotype.Service
import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.models.workflow.node.WorkflowNode
import java.util.*

/**
 * Topological sorting service using Kahn's algorithm for DAG execution order.
 *
 * This service determines the correct execution order for workflow nodes by performing
 * topological sorting with cycle detection. It uses Kahn's algorithm which provides
 * O(V+E) time complexity and enables early cycle detection.
 *
 * ## Algorithm Overview
 *
 * Kahn's algorithm works by:
 * 1. Computing in-degree (number of incoming edges) for each node
 * 2. Initializing a queue with all nodes having in-degree 0 (start nodes)
 * 3. Processing nodes from queue: dequeue, add to result, decrement successor in-degrees
 * 4. When a successor's in-degree becomes 0, add it to the queue
 * 5. If any nodes remain with in-degree > 0 after processing, a cycle exists
 *
 * ## Why Kahn's Algorithm?
 *
 * Advantages over DFS-based topological sort:
 * - Early cycle detection: identifies cycles as soon as all in-degree 0 nodes are processed
 * - Parallel execution planning: in-degree 0 nodes can run concurrently
 * - Simpler to reason about: iterative algorithm without recursion
 * - Standard algorithm: well-tested and proven correct
 *
 * ## Example Usage
 *
 * ### Linear Chain
 * ```
 * A → B → C
 *
 * Result: [A, B, C]
 * ```
 *
 * ### Diamond DAG
 * ```
 *     A
 *    / \
 *   B   C
 *    \ /
 *     D
 *
 * Valid results (both correct):
 * - [A, B, C, D]
 * - [A, C, B, D]
 * ```
 *
 * ### Cycle Detection
 * ```
 * A → B → C → A  (cycle)
 *
 * Throws: IllegalStateException("Cycle detected in workflow graph: 3 nodes unreachable")
 * ```
 *
 * ## Parallel Execution
 *
 * Nodes with in-degree 0 at any point during processing can execute in parallel:
 * ```
 *     A
 *    /|\
 *   B C D
 *    \|/
 *     E
 *
 * Parallel batches:
 * - Batch 1: [A]
 * - Batch 2: [B, C, D] (parallel execution possible)
 * - Batch 3: [E]
 * ```
 *
 * @see WorkflowNode
 * @see WorkflowEdgeEntity
 */
@Service
class WorkflowGraphTopologicalSorterService {

    /**
     * Sort nodes in topological order using Kahn's algorithm.
     *
     * Returns nodes in an order where all dependencies come before their dependents.
     * For nodes with no dependency relationship, the order is non-deterministic but
     * deterministically consistent for the same input.
     *
     * ## Algorithm Steps
     *
     * 1. **Build adjacency list**: Map each node to its outgoing edges (successors)
     * 2. **Calculate in-degrees**: Count incoming edges for each node
     * 3. **Initialize queue**: Add all nodes with in-degree 0
     * 4. **Process queue**:
     *    - Dequeue a node, add to result
     *    - For each successor, decrement in-degree
     *    - If successor in-degree becomes 0, add to queue
     * 5. **Detect cycles**: If result size != node count, throw exception
     *
     * ## Time Complexity
     *
     * O(V + E) where V = number of nodes, E = number of edges
     * - Building adjacency list: O(E)
     * - Calculating in-degrees: O(E)
     * - Processing all nodes: O(V)
     * - Processing all edges: O(E)
     *
     * ## Space Complexity
     *
     * O(V + E)
     * - Adjacency list: O(E)
     * - In-degree map: O(V)
     * - Queue: O(V) worst case
     * - Result list: O(V)
     *
     * @param nodes List of executable workflow nodes to sort
     * @param edges List of directed edges between nodes
     * @return Nodes in topologically sorted order (dependencies before dependents)
     * @throws IllegalStateException if a cycle is detected in the graph
     * @throws IllegalArgumentException if edges reference nodes not in the nodes list
     */
    fun sort(nodes: List<WorkflowNode>, edges: List<WorkflowEdgeEntity>): List<WorkflowNode> {
        if (nodes.isEmpty()) {
            return emptyList()
        }

        // Create node ID to node mapping for fast lookup
        val nodeMap = nodes.associateBy { it.id }

        // Validate that all edge endpoints exist in the node list
        validateEdges(edges, nodeMap)

        // Build adjacency list: node ID -> list of successor node IDs
        val adjacencyList = buildAdjacencyList(edges)

        // Calculate in-degree for each node (number of incoming edges)
        val inDegree = calculateInDegrees(nodes, edges)

        // Initialize queue with all nodes having in-degree 0 (start nodes)
        val queue = ArrayDeque<UUID>()
        inDegree.entries
            .filter { it.value == 0 }
            .forEach { queue.add(it.key) }

        // Process nodes in topological order
        val result = mutableListOf<WorkflowNode>()

        while (queue.isNotEmpty()) {
            // Dequeue a node with no remaining dependencies
            val nodeId = queue.removeFirst()
            val node = nodeMap[nodeId]!!
            result.add(node)

            // Process all successors (nodes this node points to)
            val successors = adjacencyList[nodeId] ?: emptyList()
            for (successorId in successors) {
                // Decrement in-degree of successor (one dependency satisfied)
                val newInDegree = inDegree[successorId]!! - 1
                inDegree[successorId] = newInDegree

                // If all dependencies satisfied, add to queue
                if (newInDegree == 0) {
                    queue.add(successorId)
                }
            }
        }

        // Detect cycles: if we haven't processed all nodes, a cycle exists
        if (result.size != nodes.size) {
            val unreachableCount = nodes.size - result.size
            val unreachableNodes = nodes.filter { it !in result }
            val unreachableIds = unreachableNodes.joinToString(", ") { it.id.toString() }

            throw IllegalStateException(
                "Cycle detected in workflow graph: $unreachableCount nodes unreachable. " +
                        "Unreachable node IDs: $unreachableIds"
            )
        }

        return result
    }

    /**
     * Build adjacency list representation of the graph.
     *
     * Maps each node ID to a list of successor node IDs (nodes it points to).
     * This enables efficient traversal during topological sort.
     *
     * @param edges List of directed edges
     * @return Map from node ID to list of successor node IDs
     */
    private fun buildAdjacencyList(edges: List<WorkflowEdgeEntity>): Map<UUID, List<UUID>> {
        return edges
            .groupBy { it.sourceNodeId }
            .mapValues { (_, edgeList) -> edgeList.mapNotNull { it.targetNodeId } }
    }

    /**
     * Calculate in-degree for each node.
     *
     * In-degree = number of incoming edges to a node.
     * Nodes with in-degree 0 have no dependencies and can execute first.
     *
     * @param nodes All workflow nodes
     * @param edges All workflow edges
     * @return Map from node ID to in-degree count
     */
    private fun calculateInDegrees(
        nodes: List<WorkflowNode>,
        edges: List<WorkflowEdgeEntity>
    ): MutableMap<UUID, Int> {
        // Initialize all nodes with in-degree 0
        val inDegree = nodes.associate { it.id to 0 }.toMutableMap()

        // Count incoming edges for each node
        for (edge in edges) {
            val targetId = edge.targetNodeId ?: continue
            inDegree[targetId] = inDegree[targetId]!! + 1
        }

        return inDegree
    }

    /**
     * Validate that all edges reference nodes in the node list.
     *
     * @param edges List of edges to validate
     * @param nodeMap Map of node IDs to nodes for fast lookup
     * @throws IllegalArgumentException if any edge references a non-existent node
     */
    private fun validateEdges(edges: List<WorkflowEdgeEntity>, nodeMap: Map<UUID, WorkflowNode>) {
        for (edge in edges) {
            if (edge.sourceNodeId !in nodeMap) {
                throw IllegalArgumentException(
                    "Edge source node ${edge.sourceNodeId} not found in node list"
                )
            }
            val targetId = edge.targetNodeId ?: continue
            if (targetId !in nodeMap) {
                throw IllegalArgumentException(
                    "Edge target node $targetId not found in node list"
                )
            }
        }
    }
}
