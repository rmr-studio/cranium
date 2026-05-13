package cranium.core.models.workflow.engine.state

import java.util.*

/**
 * Workflow execution phase representing the current state in the orchestration lifecycle.
 *
 * ## State Machine Diagram
 *
 * ```
 *     INITIALIZING
 *          |
 *          v
 *   EXECUTING_NODES ←──┐
 *          |            |
 *          v            |
 *     (nodes ready)     |
 *          |            |
 *          v            |
 *     (execute nodes)   |
 *          |            |
 *          └────────────┘
 *          |
 *          v
 *     COMPLETED / FAILED
 * ```
 *
 * ## State Transitions
 *
 * - **INITIALIZING** → **EXECUTING_NODES**: First batch of nodes ready
 * - **EXECUTING_NODES** → **EXECUTING_NODES**: More nodes ready after completions
 * - **EXECUTING_NODES** → **COMPLETED**: All nodes completed successfully
 * - **Any** → **FAILED**: Node execution error (Phase 7 will enhance error handling)
 */
enum class WorkflowExecutionPhase {
    /**
     * Building dependency graph, validating DAG, initializing active node queue.
     */
    INITIALIZING,

    /**
     * Processing active nodes. The workflow cycles in this state as nodes complete
     * and new nodes become ready for execution.
     */
    EXECUTING_NODES,

    /**
     * All nodes finished successfully. Workflow execution complete.
     */
    COMPLETED,

    /**
     * Execution error occurred. In Phase 5 (v1), any node failure transitions to this state.
     * Phase 7 will add retry logic and error recovery.
     */
    FAILED
}

/**
 * An immutable data registry used to handle state management and the orchestration of nodes.
 *
 * This data class represents the complete state of a workflow execution at a point in time.
 * All state transitions produce a new WorkflowState instance (immutable pattern).
 *
 * @property phase Current execution phase
 * @property activeNodes Set of node IDs currently executing (in Temporal Async)
 * @property completedNodes Set of node IDs that finished execution
 * @property failedNodes Set of node IDs that failed execution (empty in v1, populated in Phase 7)
 */
data class WorkflowState(
    val phase: WorkflowExecutionPhase,
    val activeNodes: Set<UUID> = emptySet(),
    val completedNodes: Set<UUID> = emptySet(),
    val failedNodes: Set<UUID> = emptySet(),
) {
    /**
     * Checks if workflow execution is finished (completed or failed).
     */
    fun isTerminal(): Boolean = phase == WorkflowExecutionPhase.COMPLETED ||
            phase == WorkflowExecutionPhase.FAILED

    /**
     * Checks if workflow has active nodes currently executing.
     */
    fun hasActiveNodes(): Boolean = activeNodes.isNotEmpty()
}

/**
 * Sealed interface representing state machine events that trigger transitions.
 *
 * Each event type corresponds to a specific workflow occurrence that requires
 * state updates. StateTransition.apply() handles event processing and produces
 * new WorkflowState instances.
 *
 * ## Event Types
 *
 * - **NodesReady**: Batch of nodes ready for execution (in-degree = 0)
 * - **NodeCompleted**: Node finished successfully with output
 * - **NodeFailed**: Node execution error (v1 basic handling, Phase 7 enhances)
 * - **AllNodesCompleted**: No more nodes to execute, all done
 * - **WorkflowFailed**: Unrecoverable workflow error
 */
sealed interface StateEvent

/**
 * Batch of nodes ready for parallel execution.
 *
 * Triggered when nodes' dependencies are satisfied (in-degree reaches 0).
 * These nodes should be executed concurrently via Temporal Async.function.
 *
 * @property nodeIds Set of ready node IDs
 */
data class NodesReady(val nodeIds: Set<UUID>) : StateEvent

/**
 * Node completed execution successfully.
 *
 * Triggered after a node's activity finishes. Node outputs are stored
 * in WorkflowDataStore (not in WorkflowState).
 *
 * @property nodeId ID of the completed node
 */
data class NodeCompleted(val nodeId: UUID) : StateEvent

/**
 * Node execution failed with error.
 *
 * In Phase 5 (v1), this transitions workflow to FAILED state immediately.
 * Phase 7 will add retry logic and error recovery strategies.
 *
 * @property nodeId ID of the failed node
 * @property error Error message describing the failure
 */
data class NodeFailed(val nodeId: UUID, val error: String) : StateEvent

/**
 * All nodes completed successfully.
 *
 * Triggered when the active node queue is empty and no more work remains.
 * Transitions workflow to COMPLETED state.
 */
data object AllNodesCompleted : StateEvent

/**
 * Unrecoverable workflow error.
 *
 * Triggered for errors outside node execution (e.g., DAG validation failure,
 * orchestration logic errors). Transitions workflow to FAILED state.
 */
data object WorkflowFailed : StateEvent
