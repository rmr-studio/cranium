package cranium.core.service.workflow.engine

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.failure.ActivityFailure
import io.temporal.workflow.Workflow
import cranium.core.configuration.workflow.RetryConfig
import cranium.core.enums.workflow.WorkflowErrorType
import cranium.core.enums.workflow.WorkflowStatus
import cranium.core.models.workflow.engine.NodeExecutionResult
import cranium.core.models.workflow.engine.WorkflowExecutionInput
import cranium.core.models.workflow.engine.WorkflowExecutionResult
import cranium.core.models.workflow.engine.state.WorkflowExecutionPhase
import cranium.core.models.workflow.engine.state.WorkflowState
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.engine.error.WorkflowExecutionError
import cranium.core.service.workflow.engine.completion.WorkflowCompletionActivity
import cranium.core.service.workflow.engine.coordinator.WorkflowCoordination
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

/**
 * Implementation of WorkflowExecutionWorkflow for deterministic workflow orchestration.
 *
 * This workflow:
 * 1. Receives workflow execution input
 * 2. Delegates to WorkflowCoordination activity for DAG execution
 * 3. Returns aggregated execution results
 *
 * DETERMINISM RULES ENFORCED:
 * - Uses Workflow.getLogger() instead of standard logging
 * - Uses Workflow.randomUUID() if random IDs needed (not used here)
 * - Uses Workflow.currentTimeMillis() if timestamps needed
 * - Uses Workflow.sideEffect() to snapshot configuration for deterministic replay
 * - NO direct database calls - all DB operations via activities
 * - NO HTTP requests - all external calls via activities
 * - NO Spring bean injection - configuration injected via Temporal's workflow factory
 *
 * Activity options configured with:
 * - StartToCloseTimeout: 5 minutes (MANDATORY)
 * - RetryOptions: Snapshotted via sideEffect from WorkflowRetryConfigurationProperties
 *
 * NOTE: This class is NOT a Spring bean. It is instantiated by Temporal's worker
 * via registerWorkflowImplementationFactory in TemporalWorkerConfiguration.
 *
 * @param retryConfig Retry configuration injected via workflow factory from application.yml.
 *                    Snapshotted via Workflow.sideEffect() at workflow start for deterministic replay.
 */
class WorkflowOrchestrationService(
    private val retryConfig: RetryConfig // Locally injected via [TemporalWorkerConfiguration.kt]
) : WorkflowOrchestration {

    private val logger = Workflow.getLogger(WorkflowOrchestrationService::class.java)

    override fun execute(input: WorkflowExecutionInput): WorkflowExecutionResult {
        logger.info("Starting workflow execution for definition: ${input.workflowDefinitionId}, nodes: ${input.nodeIds.size}")

        // Extract execution ID from Temporal workflow ID (format: "execution-{uuid}")
        val executionId = extractExecutionId()

        // Snapshot retry configuration using sideEffect for deterministic replay.
        // On initial execution: captures current retryConfig values.
        // On replay: returns the previously recorded values, ensuring identical behavior.
        val frozenRetryConfig = Workflow.sideEffect(RetryConfig::class.java) { retryConfig }

        // Create activity stubs with frozen configuration.
        val coordinationActivity = createCoordinationActivityStub(frozenRetryConfig)
        val completionActivity = createCompletionActivityStub()

        // Execute workflow and record completion
        var finalStatus = WorkflowStatus.FAILED
        var executionError: WorkflowExecutionError? = null
        var store: WorkflowDataStore? = null

        try {
            // Delegate to activity for all database operations and node execution
            store = coordinationActivity.executeWorkflowWithCoordinator(
                workflowDefinitionId = input.workflowDefinitionId,
                nodeIds = input.nodeIds,
                workspaceId = input.workspaceId
            )

            finalStatus = if (store.state.phase == WorkflowExecutionPhase.COMPLETED) {
                WorkflowStatus.COMPLETED
            } else {
                // DAG completed but with failures - build error from failed nodes
                executionError = buildErrorFromFailedNodes(store.state)
                WorkflowStatus.FAILED
            }

        } catch (e: ActivityFailure) {
            // Activity failed after all retries exhausted
            logger.error("Workflow coordination activity failed: ${e.message}")

            executionError = buildErrorFromException(e)
            finalStatus = WorkflowStatus.FAILED
        }

        // Record completion via activity (independent of success/failure)
        // This updates WorkflowExecutionEntity and queue item
        try {
            completionActivity.recordCompletion(
                executionId = executionId,
                status = finalStatus,
                error = executionError
            )
            logger.info("Recorded completion for execution $executionId: $finalStatus")
        } catch (e: Exception) {
            // Completion recording failed - log but don't fail the workflow
            // The workflow result is already determined
            logger.error("Failed to record completion for execution $executionId: ${e.message}")
        }

        return WorkflowExecutionResult(
            executionId = executionId,
            status = finalStatus,
            nodeResults = store?.let {
                it.state.completedNodes.map { nodeId ->
                    NodeExecutionResult(
                        nodeId = nodeId,
                        status = finalStatus,
                        output = it.getStepOutput(nodeId)
                    )
                }
            } ?: emptyList()
        )
    }

    /**
     * Extract execution ID from Temporal workflow ID.
     *
     * Workflow IDs are formatted as "execution-{uuid}" by ExecutionQueueProcessorService.
     * Uses sideEffect to ensure deterministic replay.
     */
    private fun extractExecutionId(): UUID {
        return Workflow.sideEffect(UUID::class.java) {
            val workflowId = Workflow.getInfo().workflowId
            UUID.fromString(workflowId.substringAfter("execution-"))
        }
    }

    /**
     * Build WorkflowExecutionError from failed nodes in workflow state.
     */
    private fun buildErrorFromFailedNodes(state: WorkflowState): WorkflowExecutionError? {
        val failedNodeId = state.failedNodes.firstOrNull() ?: return null

        return WorkflowExecutionError(
            failedNodeId = failedNodeId,
            failedNodeName = "Unknown", // Node name not available in state
            failedNodeType = "Unknown",
            errorType = WorkflowErrorType.EXECUTION_ERROR,
            message = "Workflow execution failed",
            totalRetryCount = 0,
            timestamp = Workflow.sideEffect(ZonedDateTime::class.java) { ZonedDateTime.now() }
        )
    }

    /**
     * Build WorkflowExecutionError from activity exception.
     */
    private fun buildErrorFromException(e: ActivityFailure): WorkflowExecutionError {
        // Extract error type from ApplicationFailure if available
        val errorType = try {
            val cause = e.cause
            if (cause is io.temporal.failure.ApplicationFailure) {
                WorkflowErrorType.valueOf(cause.type)
            } else {
                WorkflowErrorType.UNKNOWN_ERROR
            }
        } catch (_: Exception) {
            WorkflowErrorType.UNKNOWN_ERROR
        }

        return WorkflowExecutionError(
            failedNodeId = UUID.fromString("00000000-0000-0000-0000-000000000000"),
            failedNodeName = "Unknown",
            failedNodeType = "Unknown",
            errorType = errorType,
            message = e.cause?.message ?: e.message ?: "Unknown error",
            totalRetryCount = 0, // Retry count not easily accessible here
            timestamp = Workflow.sideEffect(ZonedDateTime::class.java) { ZonedDateTime.now() }
        )
    }

    /**
     * Creates the coordination activity stub with frozen retry configuration.
     *
     * Separated into a method for clarity. The retryConfig parameter must come from
     * Workflow.sideEffect() to ensure deterministic replay behavior.
     *
     * @param retryConfig Frozen retry configuration from sideEffect
     * @return Activity stub configured with deterministic retry options
     */
    private fun createCoordinationActivityStub(retryConfig: RetryConfig): WorkflowCoordination =
        Workflow.newActivityStub(
            WorkflowCoordination::class.java,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(5))  // MANDATORY timeout
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(retryConfig.maxAttempts)
                        .setInitialInterval(Duration.ofSeconds(retryConfig.initialIntervalSeconds))
                        .setBackoffCoefficient(retryConfig.backoffCoefficient)
                        .setMaximumInterval(Duration.ofSeconds(retryConfig.maxIntervalSeconds))
                        .setDoNotRetry(
                            // Non-retryable error types (matches WorkflowErrorType enum names)
                            "HTTP_CLIENT_ERROR",      // 4xx HTTP errors - client data won't change
                            "VALIDATION_ERROR",       // Schema/input validation - deterministic failure
                            "CONTROL_FLOW_ERROR",     // CONDITION node deterministic failure
                            "SECURITY_ERROR"          // Auth/authz errors - credentials won't change
                        )
                        .build()
                )
                .build()
        )

    /**
     * Creates the completion activity stub.
     *
     * Uses shorter timeout and fewer retries since this is a simple DB update.
     * Completion recording should not block workflow completion for too long.
     *
     * @return Activity stub for recording workflow completion
     */
    private fun createCompletionActivityStub(): WorkflowCompletionActivity =
        Workflow.newActivityStub(
            WorkflowCompletionActivity::class.java,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofSeconds(10))
                        .build()
                )
                .build()
        )
}