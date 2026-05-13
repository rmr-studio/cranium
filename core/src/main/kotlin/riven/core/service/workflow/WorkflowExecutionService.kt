package cranium.core.service.workflow

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.workflow.ExecutionQueueEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.exceptions.NotFoundException
import cranium.core.models.request.workflow.StartWorkflowExecutionRequest
import cranium.core.models.response.workflow.execution.WorkflowExecutionSummaryResponse
import cranium.core.models.workflow.engine.execution.WorkflowExecutionRecord
import cranium.core.models.workflow.engine.queue.ExecutionQueueRequest
import cranium.core.repository.workflow.WorkflowExecutionRepository
import cranium.core.repository.workflow.projection.ExecutionSummaryProjection
import cranium.core.service.activity.ActivityService
import cranium.core.service.activity.log
import cranium.core.service.auth.AuthTokenService
import cranium.core.service.workflow.queue.WorkflowExecutionQueueService
import java.util.*

/**
 * Service for managing workflow executions.
 *
 * Handles workflow lifecycle:
 * - Queuing workflow execution requests (dispatched by ExecutionDispatcherService)
 * - Persisting execution records to PostgreSQL
 * - Querying execution status and history
 * - Activity logging for audit trail
 *
 * Note: Actual Temporal dispatch is handled by ExecutionDispatcherService,
 * which processes the queue with tier-based capacity checking.
 *
 * @property workflowExecutionQueueService Queue service for enqueuing execution requests
 * @property workflowExecutionRepository Persistence for execution records
 * @property activityService Audit logging
 * @property authTokenService JWT extraction for user context
 */
@Service
class WorkflowExecutionService(
    private val workflowExecutionQueueService: WorkflowExecutionQueueService,
    private val workflowExecutionRepository: WorkflowExecutionRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger
) {

    /**
     * Start a workflow execution by queuing it for dispatch.
     *
     * This method:
     * 1. Validates workspace access (workflow definition belongs to workspace)
     * 2. Enqueues execution request via ExecutionQueueService
     * 3. Logs activity for audit trail
     * 4. Returns response indicating execution is queued
     *
     * Note: Actual Temporal dispatch happens asynchronously via ExecutionDispatcherService,
     * which checks tier-based capacity limits before dispatching.
     *
     * @param request Start workflow request with definition ID and workspace ID
     * @return Map with queueId, status, and message
     * @throws NotFoundException if workflow definition not found
     * @throws SecurityException if workflow doesn't belong to workspace
     */
    @Transactional
    fun startExecution(request: StartWorkflowExecutionRequest): ExecutionQueueRequest {
        val userId = authTokenService.getUserId()

        logger.info { "Queueing workflow execution for definition ${request.workflowDefinitionId} in workspace ${request.workspaceId}" }

        // Enqueue execution request (validation happens in queue service)
        val queueItem: ExecutionQueueEntity = workflowExecutionQueueService.enqueue(
            workspaceId = request.workspaceId,
            workflowDefinitionId = request.workflowDefinitionId
        )

        val id = requireNotNull(queueItem.id)


        logger.info { "Enqueued execution: queueId=$id" }

        // Log activity (using ENTITY as closest match for workflow execution)
        activityService.log(
            activity = Activity.ENTITY,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = queueItem.workspaceId,
            entityType = ApplicationEntityType.ENTITY,
            entityId = id,
            "type" to "workflow_execution_queued",
            "queueId" to id.toString(),
            "workflowDefinitionId" to queueItem.workflowDefinitionId.toString()
        )

        return queueItem.toModel()
    }

    // ============================================================================
    // Query Methods
    // ============================================================================

    /**
     * Get workflow execution by ID.
     *
     * Fetches a single execution with all details including input/output.
     * Verifies workspace access before returning.
     *
     * @param id Execution ID
     * @param workspaceId Workspace context for access verification
     * @return Execution details as a map
     * @throws NotFoundException if execution not found
     * @throws SecurityException if execution doesn't belong to workspace
     */
    @Transactional(readOnly = true)
    fun getExecutionById(id: UUID, workspaceId: UUID): WorkflowExecutionRecord {
        logger.info { "Getting execution by ID: $id for workspace: $workspaceId" }

        val execution = workflowExecutionRepository.findById(id)
            .orElseThrow { NotFoundException("Workflow execution not found: $id") }

        // Verify workspace access
        if (execution.workspaceId != workspaceId) {
            throw SecurityException("Workflow execution $id does not belong to workspace $workspaceId")
        }

        return execution.toModel()
    }

    /**
     * List all executions for a workflow definition.
     *
     * Returns execution summaries ordered by most recent first.
     * Verifies that all executions belong to the specified workspace.
     *
     * @param workflowDefinitionId Workflow definition ID
     * @param workspaceId Workspace context for access verification
     * @return List of execution summaries
     */
    @Transactional(readOnly = true)
    fun listExecutionsForWorkflow(workflowDefinitionId: UUID, workspaceId: UUID): List<WorkflowExecutionRecord> {
        logger.info { "Listing executions for workflow: $workflowDefinitionId in workspace: $workspaceId" }

        return workflowExecutionRepository
            .findByWorkflowDefinitionIdAndWorkspaceIdOrderByStartedAtDesc(workflowDefinitionId, workspaceId)
            .map { it.toModel() }
    }

    /**
     * List all executions for a workspace.
     *
     * Returns execution summaries across all workflows, ordered by most recent first.
     * Includes workflowDefinitionId for context.
     *
     * @param workspaceId Workspace context
     * @return List of execution summaries
     */
    @Transactional(readOnly = true)
    fun getWorkspaceExecutionRecords(workspaceId: UUID): List<WorkflowExecutionRecord> {
        logger.info { "Listing all executions for workspace: $workspaceId" }
        return workflowExecutionRepository.findByWorkspaceIdOrderByStartedAtDesc(workspaceId).map { it.toModel() }
    }

    /**
     * Get execution summary including the execution record and all node executions
     * with their associated workflow node definitions.
     *
     * Error details are surfaced in the error fields of both execution and node records:
     * - execution.error: WorkflowExecutionError (when workflow fails, contains summary and final error)
     * - node.error: NodeExecutionError (when node fails, includes errorType, message, retry history)
     *
     * The error objects are stored as JSONB in PostgreSQL and automatically serialized
     * to JSON in API responses via Hypersistence Utils JsonBinaryType.
     *
     * Uses a single JOIN query to fetch all related data efficiently.
     *
     * @param executionId Workflow execution ID
     * @param workspaceId Workspace context for access verification
     * @return Execution summary response with execution and node details
     * @throws NotFoundException if execution not found
     * @throws SecurityException if execution doesn't belong to workspace
     */
    @Throws(NotFoundException::class, SecurityException::class)
    @Transactional(readOnly = true)
    fun getExecutionSummary(
        executionId: UUID,
        workspaceId: UUID
    ): WorkflowExecutionSummaryResponse {
        logger.info { "Getting execution summary for: $executionId in workspace: $workspaceId" }

        // Fetch execution with all node executions and nodes in a single JOIN query
        val executionRecords: List<ExecutionSummaryProjection> =
            workflowExecutionRepository.findExecutionWithNodesByExecutionId(workspaceId, executionId)

        if (executionRecords.isEmpty()) {
            throw NotFoundException("Workflow execution not found: $executionId")
        }

        if (executionRecords.any {
                (it.execution.workspaceId != workspaceId) ||
                        (it.executionNode.workspaceId != workspaceId) ||
                        (it.node != null && it.node.workspaceId != workspaceId)
            }) {
            throw SecurityException("Workflow execution $executionId does not belong to workspace $workspaceId")
        }

        return WorkflowExecutionSummaryResponse(
            execution = executionRecords.first().execution.toModel(),
            nodes = executionRecords.map { projection ->
                val (_, record, node) = projection
                if (node == null) {
                    logger.warn { "Workflow node entity is null for execution node ${record.id} in execution $executionId" }
                }

                record.toModel(node)
            }
        )
    }
}
