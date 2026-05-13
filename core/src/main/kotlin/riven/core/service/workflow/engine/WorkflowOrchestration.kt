package cranium.core.service.workflow.engine

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import cranium.core.models.workflow.engine.WorkflowExecutionInput
import cranium.core.models.workflow.engine.WorkflowExecutionResult

/**
 * Temporal workflow interface for executing workflow definitions.
 *
 * This workflow orchestrates the execution of workflow nodes in sequence (v1)
 * or parallel (future enhancement) based on the DAG structure.
 *
 * IMPORTANT: Workflow implementations MUST be deterministic:
 * - NO: UUID.randomUUID(), System.currentTimeMillis(), Thread.sleep(), DB calls, HTTP requests
 * - YES: Workflow.randomUUID(), Workflow.currentTimeMillis(), Workflow.sleep(), activity invocations
 *
 * All side effects (database operations, external API calls) MUST go through activities.
 *
 * @see WorkflowOrchestrationService
 */
@WorkflowInterface
interface WorkflowOrchestration {

    /**
     * Execute a workflow definition by orchestrating its nodes.
     *
     * This method receives the workflow input, orchestrates node execution via activities,
     * and returns the aggregated results.
     *
     * @param input Workflow execution parameters (definition ID, nodes, workspace)
     * @return Execution result with status and per-node results
     */
    @WorkflowMethod
    fun execute(input: WorkflowExecutionInput): WorkflowExecutionResult
}