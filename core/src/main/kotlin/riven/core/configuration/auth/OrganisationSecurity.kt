package riven.core.configuration.auth

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.workspace.WorkspaceMember
import java.util.*

@Component
class WorkspaceSecurity {
    fun hasWorkspaceRole(workspaceId: UUID, role: WorkspaceRoles): Boolean {
        val authority: String = "ROLE_${workspaceId}_$role"

        SecurityContextHolder.getContext().authentication.let {
            if (it == null || !it.isAuthenticated) {
                return false
            }
            if (it.authorities.isEmpty()) {
                return false
            }

            return it.authorities.any { claim -> claim.authority == authority }
        }


    }

    fun hasWorkspace(workspaceId: UUID): Boolean {
        SecurityContextHolder.getContext().authentication.let {
            if (it == null || !it.isAuthenticated) {
                return false
            }
            if (it.authorities.isEmpty()) {
                return false
            }

            return it.authorities.any { claim -> claim.authority?.startsWith("ROLE_$workspaceId") == true }
        }
    }

    fun hasWorkspaceRoleOrHigher(
        workspaceId: UUID,
        targetRole: WorkspaceRoles
    ): Boolean {
        val claim: String = SecurityContextHolder.getContext().authentication.let {
            if (it == null || !it.isAuthenticated) {
                return false
            }
            if (it.authorities.isEmpty()) {
                return false
            }

            it.authorities.firstOrNull { claim -> claim.authority?.startsWith("ROLE_$workspaceId") == true } ?: return false
        }.toString()

        return WorkspaceRoles.fromString(claim.removePrefix("ROLE_${workspaceId}_")).authority >= targetRole.authority
    }

    fun hasHigherWorkspaceRole(
        workspaceId: UUID,
        targetRole: WorkspaceRoles
    ): Boolean {
        val claim: String = SecurityContextHolder.getContext().authentication.let {
            if (it == null || !it.isAuthenticated) {
                return false
            }
            if (it.authorities.isEmpty()) {
                return false
            }

            it.authorities.firstOrNull { claim -> claim.authority?.startsWith("ROLE_$workspaceId") == true } ?: return false
        }.toString()

        return WorkspaceRoles.fromString(claim.removePrefix("ROLE_${workspaceId}_")).authority > targetRole.authority
    }

    /**
     * Allow permission to update a current member (ie. Updating role, or membership removal) under the following conditions:
     *  - The user is the owner of the workspace
     *  - The user is an admin and has a role higher than the member's role (ie. ADMIN can alter roles of MEMBER users, but not OWNER or ADMIN)
     */
    fun isUpdatingWorkspaceMember(workspaceId: UUID, user: WorkspaceMember): Boolean {
        return this.hasWorkspaceRole(workspaceId, WorkspaceRoles.OWNER) ||
                (this.hasWorkspaceRoleOrHigher(workspaceId, WorkspaceRoles.ADMIN) &&
                        this.hasHigherWorkspaceRole(workspaceId, user.role))
    }

    fun isUpdatingSelf(memberId: UUID): Boolean {
        return SecurityContextHolder.getContext().authentication?.principal.let {
            if (it !is Jwt) {
                return false
            }

            it.claims["sub"]
        } == memberId.toString()
    }
}