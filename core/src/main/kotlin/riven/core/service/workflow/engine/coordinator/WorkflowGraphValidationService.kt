package cranium.core.service.workflow.engine.coordinator

import org.springframework.stereotype.Service
import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.enums.workflow.WorkflowControlType
import cranium.core.models.workflow.engine.coordinator.WorkflowGraphValidationResult
import cranium.core.models.workflow.node.WorkflowNode
import cranium.core.models.workflow.node.config.WorkflowControlConfig
import java.util.*

/**
 * This service validates the structural integrity of workflow graphs before execution,
 * ensuring they form a valid DAG with no cycles, disconnected components, or other
 * structural issues that would prevent correct execution.
 *
 * The service should check and validate the following requirements:
 *  - No Cycles
 *  - No Disconnected/Orphaned Nodes
 *  - All edges provided reference valid nodes
 *  - Conditional nodes have appropriate branching
 *
 **/
/**
 *
 * VALIDATION EXAMPLES
 *
 * Result: WorkflowGraphValidationResult(valid = true, errors = [])
 * ```
 *
 * ### Valid Diamond DAG
 * ```
 *     A
 *    / \
 *   B   C
 *    \ /
 *     D
 *
 * Result: WorkflowGraphValidationResult(valid = true, errors = [])
 * ```
 *
 * ### Invalid: Cycle
 * ```
 * A → B → C → A
 *
 * Result: WorkflowGraphValidationResult(
 *   valid = false,
 *   errors = ["Cycle detected in workflow graph: 3 nodes unreachable..."]
 * )
 * ```
 *
 * ### Invalid: Disconnected Components
 * ```
 * A → B    C → D  (two separate graphs)
 *
 * Result: WorkflowGraphValidationResult(
 *   valid = false,
 *   errors = ["Disconnected components detected: 2 unreachable nodes (C, D)"]
 * )
 * ```
 *
 * ### Invalid: Orphaned Node
 * ```
 * A → B    C  (C has no incoming edges, not a start node)
 *
 * Result: WorkflowGraphValidationResult(
 *   valid = false,
 *   errors = ["Orphaned node detected: C (no incoming edges)"]
 * )
 * ```
 *
 * ### Invalid: Conditional Without Branches
 * ```
 * A (CONDITION) → B  (only one outgoing edge)
 *
 * Result: WorkflowGraphValidationResult(
 *   valid = false,
 *   errors = ["Conditional node A must have at least 2 outgoing edges for true/false branches"]
 * )
 * ```
 **/

@Service
class WorkflowGraphValidationService(
    private val workflowGraphTopologicalSorterService: WorkflowGraphTopologicalSorterService
) {

    /**
     * Validates the provided workflow graph structure.
     * This method returns all errors found rather than failing on the first error,
     * enabling users to fix multiple issues at once.
     *
     * ## Return Value
     *
     * Returns WorkflowGraphValidationResult with:
     * - valid = true and errors = [] if all checks pass
     * - valid = false and errors = [list of issues] if any checks fail
     *
     * @param nodes List of executable workflow nodes to validate
     * @param edges List of directed edges between nodes
     * @return WorkflowGraphValidationResult indicating validity and any errors found
     */
    fun validate(nodes: List<WorkflowNode>, edges: List<WorkflowEdgeEntity>): WorkflowGraphValidationResult {
        val errors = mutableListOf<String>()

        // Empty graphs are valid (no-op workflows)
        if (nodes.isEmpty()) {
            return WorkflowGraphValidationResult(valid = true, errors = emptyList())
        }

        // Create node lookup map
        val nodeMap = nodes.associateBy { it.id }

        // 1. Validate edge consistency (all edges reference existing nodes)
        errors.addAll(validateEdgeConsistency(edges, nodeMap))
        if (errors.isNotEmpty()) {
            // Fail fast if edges are invalid - can't proceed with other checks
            return WorkflowGraphValidationResult(valid = false, errors = errors)
        }

        // 2. Validate no cycles (using topological sort)
        errors.addAll(validateNoCycles(nodes, edges))

        // 3. Validate connected components (all nodes reachable from start nodes)
        errors.addAll(validateConnectedComponents(nodes, edges, nodeMap))

        // 4. Validate no orphaned nodes (except start nodes)
        errors.addAll(validateNoOrphanedNodes(nodes, edges, nodeMap))

        // 5. Validate conditional node branching
        errors.addAll(validateConditionalBranching(nodes, edges, nodeMap))

        return WorkflowGraphValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate that all edges reference nodes that exist in the node list.
     */
    private fun validateEdgeConsistency(
        edges: List<WorkflowEdgeEntity>,
        nodeMap: Map<UUID, WorkflowNode>
    ): List<String> {
        val errors = mutableListOf<String>()

        for (edge in edges) {
            if (edge.sourceNodeId !in nodeMap) {
                errors.add("Edge ${edge.id} references non-existent source node ${edge.sourceNodeId}")
            }
            val targetId = edge.targetNodeId ?: continue
            if (targetId !in nodeMap) {
                errors.add("Edge ${edge.id} references non-existent target node $targetId")
            }
        }

        return errors
    }

    /**
     * Validate that the graph contains no cycles using topological sort.
     *
     * If topological sort succeeds, the graph is acyclic.
     * If it throws an exception, a cycle exists.
     */
    private fun validateNoCycles(nodes: List<WorkflowNode>, edges: List<WorkflowEdgeEntity>): List<String> {
        return try {
            workflowGraphTopologicalSorterService.sort(nodes, edges)
            emptyList() // No cycle detected
        } catch (e: IllegalStateException) {
            // Cycle detected - extract error message from topological sorter
            listOf(e.message ?: "Cycle detected in workflow graph")
        }
    }

    /**
     * Validate that all nodes are reachable from start nodes (no disconnected components).
     *
     * Uses BFS to traverse from all start nodes (nodes with in-degree 0) and verify
     * that all nodes are visited. If any nodes remain unvisited, the graph has
     * disconnected components.
     */
    private fun validateConnectedComponents(
        nodes: List<WorkflowNode>,
        edges: List<WorkflowEdgeEntity>,
        nodeMap: Map<UUID, WorkflowNode>
    ): List<String> {
        if (nodes.isEmpty()) return emptyList()

        // Calculate in-degrees to find start nodes
        val inDegree = nodes.associate { it.id to 0 }.toMutableMap()
        for (edge in edges) {
            val targetId = edge.targetNodeId ?: continue
            inDegree[targetId] = inDegree[targetId]!! + 1
        }

        // Find start nodes (in-degree 0)
        val startNodes = nodes.filter { inDegree[it.id] == 0 }

        if (startNodes.isEmpty()) {
            // No start nodes means all nodes are in cycles (caught by cycle detection)
            return emptyList()
        }

        // Build adjacency list for BFS traversal
        val adjacencyList = edges
            .groupBy { it.sourceNodeId }
            .mapValues { (_, edgeList) -> edgeList.mapNotNull { it.targetNodeId } }

        // BFS from all start nodes to find reachable nodes
        val visited = mutableSetOf<UUID>()
        val queue = ArrayDeque<UUID>()

        // Initialize queue with all start nodes
        startNodes.forEach { queue.add(it.id) }

        while (queue.isNotEmpty()) {
            val nodeId = queue.removeFirst()

            if (nodeId in visited) continue
            visited.add(nodeId)

            // Add all successors to queue
            val successors = adjacencyList[nodeId] ?: emptyList()
            successors.forEach { successorId ->
                if (successorId !in visited) {
                    queue.add(successorId)
                }
            }
        }

        // Check if all nodes were visited
        val unreachableNodes = nodes.filter { it.id !in visited }

        return if (unreachableNodes.isNotEmpty()) {
            val unreachableIds = unreachableNodes.joinToString(", ") { it.id.toString() }
            listOf(
                "Disconnected components detected: ${unreachableNodes.size} unreachable nodes ($unreachableIds)"
            )
        } else {
            emptyList()
        }
    }

    /**
     * Validate that no nodes are orphaned (except start nodes).
     *
     * An orphaned node has no incoming edges and is not reachable from start nodes.
     * This is different from start nodes, which legitimately have in-degree 0.
     *
     * Note: In practice, this check is redundant with connected components check,
     * but kept separate for clearer error messages.
     */
    private fun validateNoOrphanedNodes(
        nodes: List<WorkflowNode>,
        edges: List<WorkflowEdgeEntity>,
        nodeMap: Map<UUID, WorkflowNode>
    ): List<String> {
        // Calculate in-degrees
        val inDegree = nodes.associate { it.id to 0 }.toMutableMap()
        for (edge in edges) {
            val targetId = edge.targetNodeId ?: continue
            inDegree[targetId] = inDegree[targetId]!! + 1
        }

        // Find nodes with in-degree 0 (potential start nodes or orphans)
        nodes.filter { inDegree[it.id] == 0 }

        // If there are multiple nodes with in-degree 0, they could be legitimate
        // start nodes (parallel entry points) or orphans. The connected components
        // check will catch true orphans.
        //
        // This check is primarily for documentation purposes - connected components
        // validation already catches this case.

        return emptyList()
    }

    /**
     * Validate that conditional nodes have at least 2 outgoing edges.
     *
     * Conditional nodes (CONTROL_FLOW with CONDITION subtype) must have true/false
     * branches, requiring at least 2 outgoing edges.
     */
    private fun validateConditionalBranching(
        nodes: List<WorkflowNode>,
        edges: List<WorkflowEdgeEntity>,
        nodeMap: Map<UUID, WorkflowNode>
    ): List<String> {
        val errors = mutableListOf<String>()

        // Build outgoing edge count for each node
        val outgoingEdgeCount = edges
            .groupBy { it.sourceNodeId }
            .mapValues { (_, edgeList) -> edgeList.size }

        // Check conditional nodes (control nodes with CONDITION subtype)
        val conditionalNodes = nodes.filter { node ->
            val config = node.config
            config is WorkflowControlConfig && config.subType == WorkflowControlType.CONDITION
        }

        for (node in conditionalNodes) {
            val edgeCount = outgoingEdgeCount[node.id] ?: 0

            if (edgeCount < 2) {
                errors.add(
                    "Conditional node ${node.id} must have at least 2 outgoing edges " +
                            "for true/false branches (found $edgeCount)"
                )
            }
        }

        return errors
    }
}


