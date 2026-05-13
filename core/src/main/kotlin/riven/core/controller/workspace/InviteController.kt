package cranium.core.controller.workspace

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import cranium.core.enums.workspace.WorkspaceRoles
import cranium.core.models.workspace.WorkspaceInvite
import cranium.core.service.workspace.WorkspaceInviteService
import java.util.*

@RestController
@RequestMapping("/api/v1/workspace/invite")
@Tag(name = "workspace")
class InviteController(
    private val workspaceInviteService: WorkspaceInviteService
) {

    @PostMapping("/workspace/{workspaceId}/email/{email}/role/{role}")
    fun inviteToWorkspace(
        @PathVariable workspaceId: UUID,
        @PathVariable email: String,
        @PathVariable role: WorkspaceRoles
    ): ResponseEntity<WorkspaceInvite> {
        val invitation: WorkspaceInvite = workspaceInviteService.createWorkspaceInvitation(
            workspaceId = workspaceId,
            email = email,
            role = role
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(invitation)
    }

    @PostMapping("/accept/{inviteToken}")
    fun acceptInvite(
        @PathVariable inviteToken: String
    ): ResponseEntity<Unit> {
        workspaceInviteService.handleInvitationResponse(inviteToken, accepted = true)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @PostMapping("/reject/{inviteToken}")
    fun rejectInvite(
        @PathVariable inviteToken: String
    ): ResponseEntity<Unit> {
        workspaceInviteService.handleInvitationResponse(inviteToken, accepted = false)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @GetMapping("/workspace/{workspaceId}")
    fun getWorkspaceInvites(
        @PathVariable workspaceId: UUID
    ): ResponseEntity<List<WorkspaceInvite>> {
        val invites: List<WorkspaceInvite> = workspaceInviteService.getWorkspaceInvites(workspaceId)
        return ResponseEntity.ok(invites)
    }

    @GetMapping("/user")
    fun getUserInvites(
    ): ResponseEntity<List<WorkspaceInvite>> {
        val invites: List<WorkspaceInvite> = workspaceInviteService.getUserInvites()
        return ResponseEntity.ok(invites)
    }

    @DeleteMapping("/workspace/{workspaceId}/invitation/{id}")
    fun revokeInvite(
        @PathVariable workspaceId: UUID,
        @PathVariable id: UUID
    ): ResponseEntity<Unit> {
        workspaceInviteService.revokeWorkspaceInvite(workspaceId, id)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

}