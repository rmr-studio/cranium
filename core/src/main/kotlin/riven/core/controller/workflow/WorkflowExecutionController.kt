package cranium.core.controller.workflow

import io.github.oshai.kotlinlogging.KLogger
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import cranium.core.models.request.workflow.StartWorkflowExecutionRequest
import cranium.core.models.response.workflow.execution.WorkflowExecutionSummaryResponse
import cranium.core.models.workflow.engine.execution.WorkflowExecutionRecord
import cranium.core.models.workflow.engine.queue.ExecutionQueueRequest
import cranium.core.service.workflow.WorkflowExecutionService
import java.util.*

/**
 * REST controller for workflow execution operations.
 *
 * Provides endpoints for:
 * - Starting workflow executions
 * - Querying execution status
 * - Listing executions by workflow or workspace
 * - Retrieving node-level execution details
 *
 * Security: All endpoints require authentication and workspace access validation.
 */
@RestController
@RequestMapping("/api/v1/workflow/executions")
@PreAuthorize("isAuthenticated()")
@Tag(name = "workflow")
class WorkflowExecutionController(
    private val workflowExecutionService: WorkflowExecutionService,
    private val log: KLogger
) {

    /**
     * Start a workflow execution.
     *
     * Queues a workflow execution request for the specified workflow definition.
     * The workflow is dispatched asynchronously when workspace capacity is available.
     * This endpoint returns immediately with queue status.
     *
     * @param request Start execution request (workflowDefinitionId, workspaceId)
     * @return Response with queueId, status, and message
     */
    @PostMapping("/start")
    @Operation(summary = "Queue a workflow execution")
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Workflow execution queued successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Workflow definition not found")
    )
    fun startExecution(
        @RequestBody request: StartWorkflowExecutionRequest
    ): ResponseEntity<ExecutionQueueRequest> {
        log.info { "POST /api/v1/workflow/executions/start - workflowDefinitionId: ${request.workflowDefinitionId}, workspaceId: ${request.workspaceId}" }

        val response = workflowExecutionService.startExecution(request)

        return ResponseEntity.accepted().body(response)
    }

    /**
     * Get workflow execution by ID.
     *
     * Retrieves full execution details including input, output, status, and timestamps.
     * Verifies workspace access before returning.
     *
     * @param id Execution ID
     * @param workspaceId Workspace context for access verification
     * @return Execution details
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get workflow execution by ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Execution retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Execution not found")
    )
    fun getExecution(
        @PathVariable id: UUID,
        @RequestParam workspaceId: UUID
    ): ResponseEntity<WorkflowExecutionRecord> {
        log.info { "GET /api/v1/workflow/executions/$id - workspaceId: $workspaceId" }

        val response = workflowExecutionService.getExecutionById(id, workspaceId)

        return ResponseEntity.ok(response)
    }

    /**
     * List all executions for a workflow definition.
     *
     * Returns execution history ordered by most recent first.
     * Useful for monitoring and debugging workflow runs.
     *
     * @param workflowDefinitionId Workflow definition ID
     * @param workspaceId Workspace context for access verification
     * @return List of execution summaries
     */
    @GetMapping("/workflow/{workflowDefinitionId}")
    @Operation(
        summary = "List all executions for a workflow definition",
        description = "Returns execution history ordered by most recent first"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Executions retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Workflow definition not found")
    )
    fun listWorkflowExecutions(
        @PathVariable workflowDefinitionId: UUID,
        @RequestParam workspaceId: UUID
    ): ResponseEntity<List<WorkflowExecutionRecord>> {
        log.info { "GET /api/v1/workflow/executions/workflow/$workflowDefinitionId - workspaceId: $workspaceId" }

        val response = workflowExecutionService.listExecutionsForWorkflow(workflowDefinitionId, workspaceId)

        return ResponseEntity.ok(response)
    }

    /**
     * List all executions for a workspace.
     *
     * Returns all workflow executions across all workflows in the workspace,
     * ordered by most recent first.
     *
     * @param workspaceId Workspace context
     * @return List of execution summaries
     */
    @GetMapping("/workspace/{workspaceId}")
    @Operation(
        summary = "List all executions for workspace",
        description = "Returns all workflow executions across all workflows in workspace"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Executions retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access")
    )
    fun listWorkspaceExecutions(
        @PathVariable workspaceId: UUID
    ): ResponseEntity<List<WorkflowExecutionRecord>> {
        log.info { "GET /api/v1/workflow/executions/workspace/$workspaceId" }

        val response = workflowExecutionService.getWorkspaceExecutionRecords(workspaceId)

        return ResponseEntity.ok(response)
    }

    /**
     * Get execution summary including node-level execution details.
     *
     * Returns the execution record along with the execution status for each node
     * in the workflow. Useful for debugging which nodes succeeded or failed.
     *
     * @param id Execution ID
     * @param workspaceId Workspace context for access verification
     * @return Execution summary with node execution details
     */
    @GetMapping("/{id}/summary")
    @Operation(
        summary = "Get execution summary with node details",
        description = "Returns execution record and status for each node in the workflow"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Execution summary retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized access"),
        ApiResponse(responseCode = "404", description = "Execution not found")
    )
    fun getExecutionSummary(
        @PathVariable id: UUID,
        @RequestParam workspaceId: UUID
    ): ResponseEntity<WorkflowExecutionSummaryResponse> {
        log.info { "GET /api/v1/workflow/executions/$id/summary - workspaceId: $workspaceId" }

        val response = workflowExecutionService.getExecutionSummary(id, workspaceId)
        return ResponseEntity.ok(response)
    }
}
