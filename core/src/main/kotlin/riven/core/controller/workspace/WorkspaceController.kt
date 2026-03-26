package riven.core.controller.workspace

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import riven.core.enums.workspace.BusinessType
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.request.workspace.SaveWorkspaceRequest
import riven.core.models.response.catalog.TemplateInstallationResponse
import riven.core.models.workspace.Workspace
import riven.core.models.workspace.WorkspaceMember
import riven.core.service.catalog.TemplateInstallationService
import riven.core.service.workspace.WorkspaceService
import java.util.*

@RestController
@RequestMapping("/api/v1/workspace")
@Tag(name = "workspace")
class WorkspaceController(
    private val workspaceService: WorkspaceService,
    private val templateInstallationService: TemplateInstallationService,
) {


    /**
     * Retrieves an workspace by its identifier.
     *
     * @param workspaceId The UUID of the workspace to retrieve.
     * @param includeMetadata If `true`, include additional workspace metadata in the response.
     * @return The requested Workspace contained in the response body (HTTP 200).
     */
    @GetMapping("/{workspaceId}")
    fun getWorkspace(
        @PathVariable workspaceId: UUID,
        @RequestParam includeMetadata: Boolean = false
    ): ResponseEntity<Workspace> {
        val workspace: Workspace = this.workspaceService.getWorkspaceById(
            workspaceId = workspaceId,
            includeMetadata = includeMetadata
        )

        return ResponseEntity.ok(workspace)
    }

    @PostMapping("/", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun saveWorkspace(
        @RequestPart("workspace", required = true) workspace: SaveWorkspaceRequest,
        @RequestPart("file", required = false) file: MultipartFile? = null
    ): ResponseEntity<Workspace> {
        val response: Workspace = this.workspaceService.saveWorkspace(
            workspace,
            file
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/{workspaceId}")
    fun deleteWorkspace(
        @PathVariable workspaceId: UUID
    ): ResponseEntity<Void> {
        this.workspaceService.deleteWorkspace(workspaceId)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{workspaceId}/member/{memberId}")
    fun removeMemberFromWorkspace(
        @PathVariable workspaceId: UUID,
        @PathVariable memberId: UUID
    ): ResponseEntity<Void> {
        this.workspaceService.removeMemberFromWorkspace(workspaceId, memberId)
        return ResponseEntity.ok().build()
    }

    @PutMapping("/{workspaceId}/member/{memberId}/role/{role}")
    fun updateMemberRole(
        @PathVariable workspaceId: UUID,
        @PathVariable memberId: UUID,
        @PathVariable role: WorkspaceRoles
    ): ResponseEntity<WorkspaceMember> {
        val updatedMember: WorkspaceMember = this.workspaceService.updateMemberRole(
            workspaceId = workspaceId,
            memberId = memberId,
            role = role
        )
        return ResponseEntity.ok(updatedMember)
    }

    @Operation(summary = "Install or reinstall the lifecycle template for a workspace. Idempotent — returns early if already installed.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Template installed or already present"),
        ApiResponse(responseCode = "404", description = "Template not found in catalog"),
    )
    @PostMapping("/{workspaceId}/install-template")
    fun installTemplate(
        @PathVariable workspaceId: UUID,
        @RequestParam businessType: BusinessType,
    ): ResponseEntity<TemplateInstallationResponse> {
        val response = templateInstallationService.installTemplate(workspaceId, businessType.templateKey)
        return ResponseEntity.ok(response)
    }
}