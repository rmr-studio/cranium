package cranium.core.service.workflow.engine.coordinator

import org.springframework.stereotype.Service
import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.exceptions.WorkflowExecutionException
import cranium.core.exceptions.WorkflowValidationException
import cranium.core.models.common.json.JsonValue
import cranium.core.models.workflow.engine.coordinator.*
import cranium.core.models.workflow.engine.state.AllNodesCompleted
import cranium.core.models.workflow.engine.state.NodeCompleted
import cranium.core.models.workflow.engine.state.NodesReady
import cranium.core.models.workflow.engine.state.StateTransition
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.engine.state.WorkflowExecutionPhase
import cranium.core.models.workflow.engine.state.WorkflowFailed
import cranium.core.models.workflow.node.WorkflowNode
import java.util.*

/**
 * This service manages and coordinates the internal graph, and orchestrates node execution

 * ## Usage with Temporal Async/Promise
 * The coordinator delegates actual node execution to the caller via a nodeExecutor lambda.
 * This keeps the coordinator deterministic and enables Temporal parallel execution:
 *
 * TODO. Need to add
 * - Retry Policies
 * - Error Recovery Workflows
 * - Partial Completion Handling
 * - Full Conditional Branching (ie. Switches, Loops, etc)
 *
 *
 * @property dagValidator Validates DAG structure before execution
 * @property workflowGraphTopologicalSorterService Verifies execution order (used for pre-validation)
 * @property workflowGraphQueueManagementService Manages parallel node scheduling with in-degree tracking
 */
@Service
class WorkflowGraphCoordinationService(
    private val dagValidator: WorkflowGraphValidationService,
    private val workflowGraphTopologicalSorterService: WorkflowGraphTopologicalSorterService,
    private val workflowGraphQueueManagementService: WorkflowGraphQueueManagementService
) {

    /**
     * Executes provided workflow graph
     *
     * This method orchestrates the complete workflow execution:
     * 1. Validates DAG structure (throws if invalid)
     * 2. Initializes state machine and active node queue
     * 3. Executes nodes in topological order with maximum parallelism
     * 4. Tracks state transitions throughout execution
     * 5. Returns final state with all node outputs
     *
     * ## Execution Model
     *
     * The coordinator uses a pull-based scheduling model:
     * - Get batch of ready nodes (in-degree = 0)
     * - Execute batch in parallel via nodeExecutor
     * - Mark nodes completed, which may enqueue new ready nodes
     * - Repeat until all nodes completed
     *
     * This enables maximum parallelism while respecting dependencies.
     *
     * ## Node Executor Contract
     *
     * The nodeExecutor lambda must:
     * - Accept a list of ExecutableNode instances (ready for execution)
     * - Execute them in parallel (e.g., Temporal Async.function + Promise.allOf)
     * - Return a list of (nodeId, output) pairs for completed nodes
     * - Propagate exceptions for failed nodes
     *
     * ## State Machine
     *
     * State transitions during execution:
     * - INITIALIZING → EXECUTING_NODES (first batch ready)
     * - EXECUTING_NODES → EXECUTING_NODES (subsequent batches)
     * - EXECUTING_NODES → COMPLETED (all nodes done)
     * - Any → FAILED (on error)
     *
     * ## Return Value
     *
     * Returns the final WorkflowState containing:
     * - phase: COMPLETED or FAILED
     * - completedNodes: Set of all completed node IDs
     *
     * Note: Node outputs are stored in WorkflowDataStore, not in WorkflowState.
     *
     * @param nodes All workflow nodes in the DAG
     * @param edges All dependency edges (source → target)
     * @param nodeExecutor Lambda that executes a batch of nodes in parallel
     * @return Final WorkflowState with all outputs and completion status
     * @throws WorkflowValidationException if DAG structure is invalid
     * @throws IllegalStateException if execution completes with remaining nodes (deadlock)
     */
    fun executeWorkflow(
        store: WorkflowDataStore,
        nodes: List<WorkflowNode>,
        edges: List<WorkflowEdgeEntity>,
        nodeExecutor: (List<WorkflowNode>) -> List<Pair<UUID, JsonValue>>
    ) {
        // 1. Validate DAG structure
        val validationResult = dagValidator.validate(nodes, edges)
        if (!validationResult.valid) {
            throw WorkflowValidationException(
                "Invalid workflow DAG: ${validationResult.errors.joinToString("; ")}"
            )
        }

        // 2. Verify topological order exists (redundant with validator but provides extra safety)
        try {
            workflowGraphTopologicalSorterService.sort(nodes, edges)
        } catch (e: IllegalStateException) {
            throw WorkflowValidationException("Topological sort failed: ${e.message}", e)
        }


        // 4. Initialize active node queue
        workflowGraphQueueManagementService.initialize(nodes, edges)

        // 5. Handle empty workflow (no nodes)
        if (nodes.isEmpty()) {
            // Return COMPLETED state directly (no nodes to execute)
            store.apply {
                state = state.copy(phase = WorkflowExecutionPhase.COMPLETED)
            }
            return
        }

        // 6. Execution loop: process nodes in topological order with parallelism
        while (workflowGraphQueueManagementService.hasMoreWork()) {
            // Get batch of ready nodes (all nodes with in-degree = 0)
            val readyNodes = workflowGraphQueueManagementService.getReadyNodes()

            if (readyNodes.isEmpty()) {
                // No ready nodes but queue has more work = deadlock (shouldn't happen after validation)
                val remainingNodes = workflowGraphQueueManagementService.getRemainingNodes()
                throw IllegalStateException(
                    "Deadlock detected: ${remainingNodes.size} nodes remaining with unsatisfied dependencies. " +
                            "Node IDs: ${remainingNodes.joinToString(", ") { it.id.toString() }}"
                )
            }

            // Transition state: nodes ready for execution
            store.apply {
                state = StateTransition.apply(
                    state,
                    NodesReady(readyNodes.map { it.id }.toSet())
                )
            }

            // Execute ready nodes in parallel (caller handles Temporal Async/Promise)
            val results: List<Pair<UUID, Any?>> = try {
                nodeExecutor(readyNodes)
            } catch (e: Exception) {
                // Node execution failed - transition to FAILED state
                store.apply {
                    state = StateTransition.apply(state, WorkflowFailed)
                }
                throw WorkflowExecutionException("Node execution failed: ${e.message}", e)
            }

            // Mark each node as completed and update state
            // Note: Node outputs are stored in WorkflowDataStore by the coordinator
            for ((nodeId, _) in results) {
                // Transition state: node completed (output is in dataStore, not in state)
                store.apply {
                    state = StateTransition.apply(
                        state,
                        NodeCompleted(nodeId)
                    )
                }

                // Mark node completed in queue (decrements successor in-degrees, enqueues new ready nodes)
                workflowGraphQueueManagementService.markNodeCompleted(nodeId)
            }
        }

        // Finalise workflow execution by asserting that all nodes were completed
        val completedCount = workflowGraphQueueManagementService.getCompletedNodes().size
        if (completedCount != nodes.size) {
            throw IllegalStateException(
                "Incomplete execution: $completedCount of ${nodes.size} nodes completed. " +
                        "This indicates a bug in the coordinator or queue logic."
            )
        }

        store.apply {
            state = StateTransition.apply(state, AllNodesCompleted)
        }
    }
}

