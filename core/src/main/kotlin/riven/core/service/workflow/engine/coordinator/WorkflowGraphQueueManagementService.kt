package cranium.core.service.workflow.engine.coordinator

import org.springframework.stereotype.Service
import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.models.workflow.node.WorkflowNode
import java.util.*

/**
 * Active node queue for parallel workflow node scheduling with in-degree tracking.
 *
 * This service manages the execution order of workflow nodes by tracking their dependencies
 * (in-degree) and maintaining a queue of nodes ready for execution. As nodes complete,
 * it decrements the in-degree of successor nodes and enqueues those that become ready.
 *
 * The queue maintains mutable state:
 * - In-degree map: tracks remaining dependencies for each node
 * - Ready queue: FIFO queue of nodes with in-degree 0
 * - Completed set: tracks which nodes have finished execution
 * - Node lookup: enables O(1) node access by ID
 * - Edge adjacency list: maps source node to outgoing edges
 *
 * Call `initialize()` to reset all state before reuse.
 */
@Service
class WorkflowGraphQueueManagementService {

    /**
     * Mutable in-degree map: nodeId → remaining dependency count
     */
    private val inDegreeMap = mutableMapOf<UUID, Int>()

    /**
     * FIFO queue of nodes ready for execution (in-degree = 0)
     */
    private val readyQueue = ArrayDeque<WorkflowNode>()

    /**
     * Set of completed node IDs (for tracking progress)
     */
    private val completedNodes = mutableSetOf<UUID>()

    /**
     * Node lookup map for O(1) access: nodeId → ExecutableNode
     */
    private val nodeMap = mutableMapOf<UUID, WorkflowNode>()

    /**
     * Edge adjacency list: sourceNodeId → outgoing edges
     */
    private val adjacencyList = mutableMapOf<UUID, MutableList<WorkflowEdgeEntity>>()

    /**
     * Initializes the queue with workflow nodes and edges.
     *
     * This method:
     * 1. Clears all existing state
     * 2. Calculates initial in-degree for each node
     * 3. Enqueues nodes with in-degree 0 (no dependencies)
     *
     * @param nodes All executable workflow nodes in the DAG
     * @param edges All dependency edges in the DAG
     */
    fun initialize(nodes: List<WorkflowNode>, edges: List<WorkflowEdgeEntity>) {
        // Clear all state
        inDegreeMap.clear()
        readyQueue.clear()
        completedNodes.clear()
        nodeMap.clear()
        adjacencyList.clear()

        // Build node lookup map
        nodes.forEach { node ->
            nodeMap[node.id] = node
            inDegreeMap[node.id] = 0  // Initialize all to 0
            adjacencyList[node.id] = mutableListOf()
        }

        // Calculate in-degree from edges
        edges.forEach { edge ->
            val targetId = edge.targetNodeId ?: return@forEach
            // Increment target node's in-degree
            inDegreeMap[targetId] = (inDegreeMap[targetId] ?: 0) + 1

            // Add edge to source node's adjacency list
            adjacencyList[edge.sourceNodeId]?.add(edge)
        }

        // Enqueue all nodes with in-degree 0
        inDegreeMap.forEach { (nodeId, inDegree) ->
            if (inDegree == 0) {
                nodeMap[nodeId]?.let { readyQueue.add(it) }
            }
        }
    }

    /**
     * Returns all currently ready nodes and clears the ready queue.
     *
     * This method has batch semantics: it returns all nodes currently ready for
     * execution, allowing them to be processed in parallel. The ready queue is
     * cleared after this call.
     *
     * Call this method repeatedly as nodes complete to get successive batches
     * of ready nodes.
     *
     * @return List of nodes ready for execution (in-degree = 0), or empty if none ready
     */
    fun getReadyNodes(): List<WorkflowNode> {
        val batch = readyQueue.toList()
        readyQueue.clear()
        return batch
    }

    /**
     * Marks a node as completed and updates successor nodes.
     *
     * This method:
     * 1. Adds the node to the completed set
     * 2. Decrements the in-degree of all successor nodes
     * 3. Enqueues successors whose in-degree reaches 0
     *
     * @param nodeId ID of the completed node
     * @throws IllegalArgumentException if node ID is not found in the graph
     */
    fun markNodeCompleted(nodeId: UUID) {
        require(nodeMap.containsKey(nodeId)) {
            "Node $nodeId not found in workflow graph"
        }

        // Add to completed set
        completedNodes.add(nodeId)

        // Get all outgoing edges from this node
        val outgoingEdges = adjacencyList[nodeId] ?: emptyList()

        // Decrement in-degree of successor nodes
        outgoingEdges.forEach { edge ->
            val targetId = edge.targetNodeId ?: return@forEach
            val currentInDegree = inDegreeMap[targetId] ?: 0

            if (currentInDegree > 0) {
                val newInDegree = currentInDegree - 1
                inDegreeMap[targetId] = newInDegree

                // If in-degree reaches 0, node is ready for execution
                if (newInDegree == 0) {
                    nodeMap[targetId]?.let { readyQueue.add(it) }
                }
            }
        }
    }

    /**
     * Checks if there is more work to be done.
     *
     * Returns true if:
     * - The ready queue is not empty (nodes waiting to execute), OR
     * - There are nodes with in-degree > 0 (waiting for dependencies)
     *
     * Returns false if all nodes are completed.
     *
     * @return true if execution should continue, false if all nodes are done
     */
    fun hasMoreWork(): Boolean {
        // Work remains if ready queue has nodes
        if (readyQueue.isNotEmpty()) {
            return true
        }

        // Work remains if any node has positive in-degree (waiting for dependencies)
        return inDegreeMap.any { (nodeId, inDegree) ->
            inDegree > 0 && nodeId !in completedNodes
        }
    }

    /**
     * Returns nodes that have not completed and still have dependencies.
     *
     * This is useful for debugging or error reporting to identify which nodes
     * are blocked waiting for dependencies.
     *
     * @return List of nodes with in-degree > 0 (waiting for dependencies)
     */
    fun getRemainingNodes(): List<WorkflowNode> {
        return inDegreeMap
            .filter { (nodeId, inDegree) -> inDegree > 0 && nodeId !in completedNodes }
            .mapNotNull { (nodeId, _) -> nodeMap[nodeId] }
    }

    /**
     * Returns the set of completed node IDs.
     *
     * @return Immutable set of completed node IDs
     */
    fun getCompletedNodes(): Set<UUID> = completedNodes.toSet()

    /**
     * Returns the current in-degree of a node.
     *
     * @param nodeId ID of the node to query
     * @return Current in-degree (remaining dependency count), or null if node not found
     */
    fun getInDegree(nodeId: UUID): Int? = inDegreeMap[nodeId]
}
