package cranium.core.models.workflow.engine.state

/**
 * Pure state transition logic for workflow state machine.
 *
 * This object provides the `apply()` function that processes state events and produces
 * new WorkflowState instances. All transitions are pure (no side effects) and immutable.
 *
 * ## Usage Pattern
 *
 * ```kotlin
 * var state = WorkflowState(phase = WorkflowExecutionPhase.INITIALIZING)
 *
 * // Apply events to transition state
 * state = StateTransition.apply(state, NodesReady(setOf(node1, node2)))
 * state = StateTransition.apply(state, NodeCompleted(node1, outputData))
 * state = StateTransition.apply(state, AllNodesCompleted)
 * ```
 *
 * ## Design Principles
 *
 * - **Immutability**: All transitions return new WorkflowState instances
 * - **Pure functions**: No side effects, same input always produces same output
 * - **Validation**: Throws IllegalStateException for invalid transitions
 * - **No business logic**: Only state transitions (orchestration logic is in coordinator)
 *
 * ## State Machine Diagram
 *
 * ```
 *     INITIALIZING
 *          |
 *          | NodesReady
 *          v
 *   EXECUTING_NODES ←──────┐
 *          |                |
 *          | NodeCompleted  | NodesReady
 *          |                | (more nodes ready)
 *          └────────────────┘
 *          |
 *          | AllNodesCompleted
 *          v
 *     COMPLETED
 *
 *   Any state → FAILED (NodeFailed or WorkflowFailed)
 * ```
 *
 * ## Error Handling (Phase 5 v1)
 *
 * In the current implementation, any NodeFailed event transitions directly to FAILED state.
 * Phase 7 will enhance this with:
 * - Retry strategies
 * - Error recovery workflows
 * - Partial completion handling
 * - Compensation logic
 */
object StateTransition {

    /**
     * Applies a state event to produce a new workflow state.
     *
     * This is a pure function: given the same current state and event, it always
     * produces the same new state. No side effects occur.
     *
     * @param currentState Current workflow state
     * @param event State event triggering the transition
     * @return New workflow state after applying the event
     * @throws IllegalStateException if the transition is invalid for the current state
     */
    fun apply(currentState: WorkflowState, event: StateEvent): WorkflowState {
        return when (event) {
            is NodesReady -> handleNodesReady(currentState, event)
            is NodeCompleted -> handleNodeCompleted(currentState, event)
            is NodeFailed -> handleNodeFailed(currentState, event)
            is AllNodesCompleted -> handleAllNodesCompleted(currentState)
            is WorkflowFailed -> handleWorkflowFailed(currentState)
        }
    }

    /**
     * Handles NodesReady event: batch of nodes ready for execution.
     *
     * Transitions:
     * - INITIALIZING → EXECUTING_NODES (first batch)
     * - EXECUTING_NODES → EXECUTING_NODES (subsequent batches)
     *
     * Updates:
     * - Adds nodeIds to activeNodes set
     * - Sets phase to EXECUTING_NODES
     */
    private fun handleNodesReady(state: WorkflowState, event: NodesReady): WorkflowState {
        require(event.nodeIds.isNotEmpty()) {
            "NodesReady event must contain at least one node"
        }

        // Validate nodes aren't already completed or active
        val alreadyCompleted = event.nodeIds.intersect(state.completedNodes)
        require(alreadyCompleted.isEmpty()) {
            "Cannot mark nodes as ready that are already completed: $alreadyCompleted"
        }

        val alreadyActive = event.nodeIds.intersect(state.activeNodes)
        require(alreadyActive.isEmpty()) {
            "Cannot mark nodes as ready that are already active: $alreadyActive"
        }

        return state.copy(
            phase = WorkflowExecutionPhase.EXECUTING_NODES,
            activeNodes = state.activeNodes + event.nodeIds
        )
    }

    /**
     * Handles NodeCompleted event: node finished successfully.
     *
     * Valid from: EXECUTING_NODES
     *
     * Updates:
     * - Removes nodeId from activeNodes
     * - Adds nodeId to completedNodes
     *
     * Note: Node outputs are stored in WorkflowDataStore, not in WorkflowState.
     */
    private fun handleNodeCompleted(state: WorkflowState, event: NodeCompleted): WorkflowState {
        require(state.phase == WorkflowExecutionPhase.EXECUTING_NODES) {
            "Cannot complete node in phase ${state.phase}, must be EXECUTING_NODES"
        }

        require(event.nodeId in state.activeNodes) {
            "Cannot complete node ${event.nodeId} that is not active"
        }

        require(event.nodeId !in state.completedNodes) {
            "Node ${event.nodeId} already completed"
        }

        return state.copy(
            activeNodes = state.activeNodes - event.nodeId,
            completedNodes = state.completedNodes + event.nodeId
        )
    }

    /**
     * Handles NodeFailed event: node execution error.
     *
     * In Phase 5 (v1), this immediately transitions to FAILED state.
     * Phase 7 will add retry logic and error recovery.
     *
     * Valid from: EXECUTING_NODES
     *
     * Updates:
     * - Sets phase to FAILED
     * - Removes nodeId from activeNodes
     * - Adds nodeId to failedNodes
     */
    private fun handleNodeFailed(state: WorkflowState, event: NodeFailed): WorkflowState {
        require(state.phase == WorkflowExecutionPhase.EXECUTING_NODES) {
            "Cannot fail node in phase ${state.phase}, must be EXECUTING_NODES"
        }

        require(event.nodeId in state.activeNodes) {
            "Cannot fail node ${event.nodeId} that is not active"
        }

        return state.copy(
            phase = WorkflowExecutionPhase.FAILED,
            activeNodes = state.activeNodes - event.nodeId,
            failedNodes = state.failedNodes + event.nodeId
        )
    }

    /**
     * Handles AllNodesCompleted event: workflow finished successfully.
     *
     * Valid from: EXECUTING_NODES
     *
     * Validation:
     * - activeNodes must be empty (all nodes finished)
     *
     * Updates:
     * - Sets phase to COMPLETED
     */
    private fun handleAllNodesCompleted(state: WorkflowState): WorkflowState {
        require(state.phase == WorkflowExecutionPhase.EXECUTING_NODES) {
            "Cannot complete workflow in phase ${state.phase}, must be EXECUTING_NODES"
        }

        require(state.activeNodes.isEmpty()) {
            "Cannot complete workflow with active nodes: ${state.activeNodes}"
        }

        return state.copy(
            phase = WorkflowExecutionPhase.COMPLETED
        )
    }

    /**
     * Handles WorkflowFailed event: unrecoverable error.
     *
     * Valid from: Any phase (except COMPLETED or already FAILED)
     *
     * This handles errors outside of node execution, such as:
     * - DAG validation failures
     * - Orchestration logic errors
     * - Infrastructure failures
     *
     * Updates:
     * - Sets phase to FAILED
     */
    private fun handleWorkflowFailed(state: WorkflowState): WorkflowState {
        require(!state.isTerminal()) {
            "Cannot transition to FAILED from terminal state ${state.phase}"
        }

        return state.copy(
            phase = WorkflowExecutionPhase.FAILED
        )
    }
}