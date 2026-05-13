package cranium.core.service.workflow.engine.coordinator

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import java.util.*

/**
 * Temporal activity interface for executing workflow nodes.
 *
 * Activities handle all non-deterministic operations:
 * - Database queries and writes
 * - External API calls
 * - Random number generation
 * - System time access
 *
 * Activities are stateless (singleton instances handle concurrent executions).
 * All data must be passed via method parameters, not stored in instance fields.
 *
 * @see WorkflowCoordinationService
 */
@ActivityInterface
interface WorkflowCoordination {

    /**
     * Execute workflow with DAG coordination for parallel node scheduling.
     *
     * This activity:
     * 1. Fetches nodes and edges from database
     * 2. Delegates to DagExecutionCoordinator for orchestration
     * 3. Validates DAG structure
     * 4. Initializes state machine and active node queue
     * 5. Executes nodes in topological order with maximum parallelism
     * 6. Returns final WorkflowState with all outputs
     *
     * Note: Node execution happens synchronously within this activity (not via Temporal Async).
     * The coordinator's parallel execution is simulated via sequential processing in v1.
     * Future enhancement: Use Temporal child workflows for true parallel execution.
     *
     * @param workflowDefinitionId The workflow definition ID
     * @param nodeIds List of node IDs to execute
     * @param workspaceId Workspace context
     * @return Final workflow state with completion status and outputs
     */
    @ActivityMethod
    fun executeWorkflowWithCoordinator(
        workflowDefinitionId: UUID,
        nodeIds: List<UUID>,
        workspaceId: UUID
    ): WorkflowDataStore
}
