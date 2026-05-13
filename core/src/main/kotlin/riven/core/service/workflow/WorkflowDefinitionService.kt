package cranium.core.service.workflow

import io.github.oshai.kotlinlogging.KLogger
import jakarta.transaction.Transactional
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import cranium.core.entity.workflow.WorkflowDefinitionEntity
import cranium.core.entity.workflow.WorkflowDefinitionVersionEntity
import cranium.core.enums.activity.Activity
import cranium.core.enums.core.ApplicationEntityType
import cranium.core.enums.util.OperationType
import cranium.core.enums.workflow.WorkflowDefinitionStatus
import cranium.core.exceptions.NotFoundException
import cranium.core.models.request.workflow.CreateWorkflowDefinitionRequest
import cranium.core.models.request.workflow.SaveWorkflowDefinitionRequest
import cranium.core.models.request.workflow.UpdateWorkflowDefinitionRequest
import cranium.core.models.response.workflow.SaveWorkflowDefinitionResponse
import cranium.core.models.workflow.WorkflowDefinition
import cranium.core.models.workflow.WorkflowEdge
import cranium.core.models.workflow.WorkflowGraph
import cranium.core.models.workflow.WorkflowGraphReference
import cranium.core.models.workflow.node.WorkflowNode
import cranium.core.repository.workflow.WorkflowDefinitionRepository
import cranium.core.repository.workflow.WorkflowDefinitionVersionRepository
import cranium.core.repository.workflow.WorkflowEdgeRepository
import cranium.core.repository.workflow.WorkflowNodeRepository
import cranium.core.models.common.markDeleted
import cranium.core.service.activity.ActivityService
import cranium.core.service.activity.log
import cranium.core.service.auth.AuthTokenService
import cranium.core.util.ServiceUtil.findOrThrow
import java.util.*

/**
 * Service for managing workflow definition lifecycle.
 *
 * Provides CRUD operations for workflow definitions including:
 * - Creating new workflow definitions with initial version
 * - Retrieving workflow definitions by ID or workspace
 * - Updating workflow definition metadata (name, description, icon, tags)
 * - Soft-deleting workflow definitions
 *
 * Note: Workflow/canvas structure updates are handled separately by the
 * workflow graph management service.
 */
@Service
class WorkflowDefinitionService(
    private val workflowDefinitionRepository: WorkflowDefinitionRepository,
    private val workflowDefinitionVersionRepository: WorkflowDefinitionVersionRepository,
    private val workflowNodeRepository: WorkflowNodeRepository,
    private val workflowEdgeRepository: WorkflowEdgeRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService,
    private val logger: KLogger
) {

    /**
     * Creates a new workflow definition with an initial empty version.
     *
     * @param workspaceId The workspace to create the workflow in
     * @param request The creation request containing name, description, icon, and tags
     * @return The created workflow definition
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun createWorkflow(workspaceId: UUID, request: CreateWorkflowDefinitionRequest): WorkflowDefinition {
        val userId = authTokenService.getUserId()
        logger.info { "Creating workflow definition '${request.name}' in workspace $workspaceId" }

        // Create workflow definition entity
        val definitionEntity = WorkflowDefinitionEntity(
            workspaceId = workspaceId,
            name = request.name,
            description = request.description,
            versionNumber = 1,
            status = WorkflowDefinitionStatus.DRAFT,
            iconColour = request.iconColour,
            iconType = request.iconType,
            tags = request.tags,
        )

        val savedDefinition = workflowDefinitionRepository.save(definitionEntity)
        val definitionId = requireNotNull(savedDefinition.id) { "Saved definition must have an ID" }

        // Create initial version with empty workflow and canvas
        val versionEntity = WorkflowDefinitionVersionEntity(
            workspaceId = workspaceId,
            workflowDefinitionId = definitionId,
            versionNumber = 1,
            workflow = WorkflowGraphReference(
                nodeIds = setOf<UUID>(),
                edgeIds = setOf<UUID>()
            ),
            canvas = emptyMap<String, Any>(),
        )

        val savedVersion = workflowDefinitionVersionRepository.save(versionEntity)

        // Log activity
        activityService.log(
            activity = Activity.WORKFLOW_DEFINITION,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.WORKFLOW_DEFINITION,
            entityId = definitionId,
            "name" to request.name,
            "version" to 1,
            "status" to WorkflowDefinitionStatus.DRAFT.name
        )

        logger.info { "Created workflow definition $definitionId with version 1" }
        return savedDefinition.toModel(savedVersion)
    }

    /**
     * Saves a workflow definition (create or update).
     *
     * If [SaveWorkflowDefinitionRequest.id] is null, creates a new workflow definition.
     * If [SaveWorkflowDefinitionRequest.id] is provided, updates the existing workflow definition.
     *
     * For updates, only metadata is updated (name, description, icon, tags).
     * Workflow/canvas structure is managed separately.
     *
     * @param workspaceId The workspace to save the workflow in
     * @param request The save request containing workflow data
     * @return Response containing the saved workflow definition and created flag
     * @throws NotFoundException if updating and the workflow is not found
     * @throws AccessDeniedException if updating and the workflow belongs to a different workspace
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun saveWorkflow(workspaceId: UUID, request: SaveWorkflowDefinitionRequest): SaveWorkflowDefinitionResponse {
        return if (request.id == null) {
            // Create new workflow
            val createRequest = CreateWorkflowDefinitionRequest(
                name = request.name,
                description = request.description,
                iconColour = request.iconColour,
                iconType = request.iconType,
                tags = request.tags
            )
            val definition = createWorkflow(workspaceId, createRequest)
            SaveWorkflowDefinitionResponse(definition = definition, created = true)
        } else {
            // Update existing workflow
            val updateRequest = UpdateWorkflowDefinitionRequest(
                name = request.name,
                description = request.description,
                iconColour = request.iconColour,
                iconType = request.iconType,
                tags = request.tags
            )
            val definition = updateWorkflow(request.id, workspaceId, updateRequest)
            SaveWorkflowDefinitionResponse(definition = definition, created = false)
        }
    }

    /**
     * Retrieves a workflow definition by ID.
     *
     * @param id The workflow definition ID
     * @param workspaceId The workspace ID for access verification
     * @return The workflow definition
     * @throws NotFoundException if the workflow definition is not found
     * @throws AccessDeniedException if the workflow belongs to a different workspace
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getWorkflowById(id: UUID, workspaceId: UUID): WorkflowDefinition {
        logger.debug { "Fetching workflow definition $id for workspace $workspaceId" }

        val definition = findOrThrow {
            workflowDefinitionRepository.findById(id)
        }

        // Verify workspace access
        if (definition.workspaceId != workspaceId) {
            logger.warn { "Workspace mismatch: workflow $id belongs to ${definition.workspaceId}, not $workspaceId" }
            throw AccessDeniedException("Workflow definition does not belong to the specified workspace")
        }

        // Check if soft-deleted
        if (definition.deleted) {
            throw NotFoundException("Workflow definition not found")
        }

        // Fetch the current version
        val version = workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(
            id,
            definition.versionNumber
        ) ?: throw NotFoundException("Workflow version not found")

        val nodes: List<WorkflowNode> =
            workflowNodeRepository.findByWorkspaceIdAndIdIn(workspaceId, version.workflow.nodeIds).map { it.toModel() }
        val edges: List<WorkflowEdge> =
            workflowEdgeRepository.findByWorkspaceIdAndNodeIds(workspaceId, nodes.map { it.id }.toTypedArray()).let {
                WorkflowEdge.createEdges(nodes, it)
            }

        return definition.toModel(version, WorkflowGraph(workflowDefinitionId = definition.id!!, nodes, edges))
    }

    /**
     * Lists all workflow definitions for a workspace.
     *
     * @param workspaceId The workspace ID
     * @return List of workflow definitions in the workspace
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listWorkflowsForWorkspace(workspaceId: UUID): List<WorkflowDefinition> {
        logger.debug { "Listing workflow definitions for workspace $workspaceId" }

        val definitions = workflowDefinitionRepository.findByWorkspaceId(workspaceId)
        return definitions.map { definition ->
            val version = workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(
                definition.id!!,
                definition.versionNumber
            ) ?: throw NotFoundException("Workflow version not found for definition ${definition.id}")

            definition.toModel(version)
        }
    }

    /**
     * Updates workflow definition metadata.
     *
     * Only updates provided fields (non-null values in the request).
     * Does NOT update workflow/canvas structure.
     *
     * @param id The workflow definition ID
     * @param workspaceId The workspace ID for access verification
     * @param request The update request with optional fields
     * @return The updated workflow definition
     * @throws NotFoundException if the workflow definition is not found
     * @throws AccessDeniedException if the workflow belongs to a different workspace
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun updateWorkflow(
        id: UUID,
        workspaceId: UUID,
        request: UpdateWorkflowDefinitionRequest
    ): WorkflowDefinition {
        val userId = authTokenService.getUserId()
        logger.info { "Updating workflow definition $id in workspace $workspaceId" }

        val definition = findOrThrow {
            workflowDefinitionRepository.findById(id)
        }

        // Verify workspace access
        if (definition.workspaceId != workspaceId) {
            logger.warn { "Workspace mismatch: workflow $id belongs to ${definition.workspaceId}, not $workspaceId" }
            throw AccessDeniedException("Workflow definition does not belong to the specified workspace")
        }

        // Apply updates (only non-null fields)
        val updatedDefinition = definition.copy(
            name = request.name ?: definition.name,
            description = request.description ?: definition.description,
            iconColour = request.iconColour ?: definition.iconColour,
            iconType = request.iconType ?: definition.iconType,
            tags = request.tags ?: definition.tags
        )

        val savedDefinition = workflowDefinitionRepository.save(updatedDefinition)

        // Fetch the current version
        val version = workflowDefinitionVersionRepository.findByWorkflowDefinitionIdAndVersionNumber(
            id,
            savedDefinition.versionNumber
        ) ?: throw NotFoundException("Workflow version not found")

        // Log activity
        activityService.log(
            activity = Activity.WORKFLOW_DEFINITION,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.WORKFLOW_DEFINITION,
            entityId = id,
            "name" to savedDefinition.name,
            "updatedFields" to listOfNotNull(
                if (request.name != null) "name" else null,
                if (request.description != null) "description" else null,
                if (request.iconColour != null) "iconColour" else null,
                if (request.iconType != null) "iconType" else null,
                if (request.tags != null) "tags" else null
            )
        )

        logger.info { "Updated workflow definition $id" }
        return savedDefinition.toModel(version)
    }

    /**
     * Soft-deletes a workflow definition.
     *
     * Sets deleted=true and deletedAt=now.
     *
     * @param id The workflow definition ID
     * @param workspaceId The workspace ID for access verification
     * @throws NotFoundException if the workflow definition is not found
     * @throws AccessDeniedException if the workflow belongs to a different workspace
     */
    @Transactional
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteWorkflow(id: UUID, workspaceId: UUID) {
        val userId = authTokenService.getUserId()
        logger.info { "Deleting workflow definition $id in workspace $workspaceId" }

        val definition = findOrThrow {
            workflowDefinitionRepository.findById(id)
        }

        // Verify workspace access
        if (definition.workspaceId != workspaceId) {
            logger.warn { "Workspace mismatch: workflow $id belongs to ${definition.workspaceId}, not $workspaceId" }
            throw AccessDeniedException("Workflow definition does not belong to the specified workspace")
        }

        // Check if already deleted
        if (definition.deleted) {
            throw NotFoundException("Workflow definition not found")
        }

        // Soft delete
        definition.markDeleted()
        workflowDefinitionRepository.save(definition)

        // Log activity
        activityService.log(
            activity = Activity.WORKFLOW_DEFINITION,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.WORKFLOW_DEFINITION,
            entityId = id,
            "name" to definition.name
        )

        logger.info { "Soft-deleted workflow definition $id" }
    }
}
