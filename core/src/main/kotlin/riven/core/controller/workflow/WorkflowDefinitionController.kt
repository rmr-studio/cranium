package cranium.core.controller.workflow

import io.github.oshai.kotlinlogging.KLogger
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import cranium.core.models.request.workflow.SaveWorkflowDefinitionRequest
import cranium.core.models.response.workflow.SaveWorkflowDefinitionResponse
import cranium.core.models.workflow.WorkflowDefinition
import cranium.core.service.workflow.WorkflowDefinitionService
import cranium.core.service.workflow.WorkflowNodeConfigRegistry
import cranium.core.service.workflow.WorkflowNodeMetadata
import java.util.*


/**
 * REST controller for workflow definition management.
 *
 * Provides CRUD endpoints for workflow definitions:
 * - Create new workflow definitions
 * - Retrieve workflow definitions by ID
 * - List workflow definitions for a workspace
 * - Update workflow definition metadata
 * - Delete (soft-delete) workflow definitions
 *
 * Security: All endpoints require authentication and workspace access validation.
 */
@RestController
@RequestMapping("/api/v1/workflow/definitions")
@Tag(name = "workflow")
@PreAuthorize("isAuthenticated()")
class WorkflowDefinitionController(
    private val workflowDefinitionService: WorkflowDefinitionService,
    private val workflowNodeConfigRegistry: WorkflowNodeConfigRegistry,
    private val logger: KLogger
) {

    /**
     * Save a workflow definition (create or update).
     *
     * If request.id is null, creates a new workflow definition with an initial empty version.
     * If request.id is provided, updates the existing workflow definition metadata.
     *
     * @param workspaceId The workspace to save the workflow in
     * @param request The save request containing workflow data
     * @return The saved workflow definition
     */
    @PostMapping("/workspace/{workspaceId}")
    @Operation(
        summary = "Save a workflow definition",
        description = "Saves a workflow definition - creates new if id is null, updates existing if id is provided. Only metadata is updated on existing definitions."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workflow definition saved successfully"),
        ApiResponse(responseCode = "400", description = "Invalid request data"),
        ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
        ApiResponse(responseCode = "404", description = "Workflow definition not found (for updates)")
    )
    fun saveWorkflow(
        @PathVariable workspaceId: UUID,
        @RequestBody request: SaveWorkflowDefinitionRequest
    ): ResponseEntity<SaveWorkflowDefinitionResponse> {
        logger.info { "POST /api/v1/workflow/definitions/workspace/$workspaceId - id: ${request.id}, name: ${request.name}" }

        val response = workflowDefinitionService.saveWorkflow(workspaceId, request)

        return ResponseEntity.ok(response)
    }

    /**
     * Get a workflow definition by ID.
     *
     * @param id The workflow definition ID
     * @param workspaceId The workspace ID for access verification
     * @return The workflow definition
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "Get workflow definition by ID",
        description = "Retrieves a workflow definition by its ID. Requires workspace access."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workflow definition retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
        ApiResponse(responseCode = "404", description = "Workflow definition not found")
    )
    fun getWorkflow(
        @PathVariable id: UUID,
        @RequestParam workspaceId: UUID
    ): ResponseEntity<WorkflowDefinition> {
        logger.info { "GET /api/v1/workflow/definitions/$id?workspaceId=$workspaceId" }

        val workflow = workflowDefinitionService.getWorkflowById(id, workspaceId)

        return ResponseEntity.ok(workflow)
    }

    /**
     * List all workflow definitions for a workspace.
     *
     * @param workspaceId The workspace ID
     * @return List of workflow definitions in the workspace
     */
    @GetMapping("/workspace/{workspaceId}")
    @Operation(
        summary = "List all workflow definitions for workspace",
        description = "Retrieves all workflow definitions associated with the specified workspace."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Workflow definitions retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized - authentication required")
    )
    fun listWorkflows(
        @PathVariable workspaceId: UUID
    ): ResponseEntity<List<WorkflowDefinition>> {
        logger.info { "GET /api/v1/workflow/definitions/workspace/$workspaceId" }

        val workflows = workflowDefinitionService.listWorkflowsForWorkspace(workspaceId)

        return ResponseEntity.ok(workflows)
    }

    /**
     * Delete a workflow definition.
     *
     * Performs a soft delete - sets deleted=true and deletedAt=now.
     *
     * @param id The workflow definition ID
     * @param workspaceId The workspace ID for access verification
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete workflow definition",
        description = "Soft-deletes a workflow definition. The definition can be restored if needed."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Workflow definition deleted successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized - authentication required"),
        ApiResponse(responseCode = "404", description = "Workflow definition not found")
    )
    fun deleteWorkflow(
        @PathVariable id: UUID,
        @RequestParam workspaceId: UUID
    ): ResponseEntity<Void> {
        logger.info { "DELETE /api/v1/workflow/definitions/$id?workspaceId=$workspaceId" }

        workflowDefinitionService.deleteWorkflow(id, workspaceId)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/nodes")
    @Operation(
        summary = "Get Workflow Node Configuration",
        description = "Retrieves the configuration and metadata for all workflow nodes. " +
                "Returns a map where keys are node identifiers (e.g., 'ACTION.CREATE_ENTITY') " +
                "and values represent its display configs and schema."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Node configuration schemas retrieved successfully"),
        ApiResponse(responseCode = "401", description = "Unauthorized - authentication required")
    )
    fun getNodeDefinitions(): ResponseEntity<Map<String, WorkflowNodeMetadata>> {
        logger.info { "GET /api/v1/workflow/definitions/nodes" }

        val schemas = workflowNodeConfigRegistry.getAllNodes()

        return ResponseEntity.ok(schemas)
    }

}
