package cranium.core.configuration.auth

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import cranium.core.enums.workspace.WorkspaceRoles
import java.util.*

@Component
class CustomAuthenticationTokenConverter :
    Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val authorities = mutableListOf<SimpleGrantedAuthority>()

        // Extract standard authorities if they exist
        val scope = jwt.getClaimAsString("scope")
        scope?.split(" ")?.forEach { s ->
            authorities.add(SimpleGrantedAuthority("SCOPE_$s"))
        }

        // Extract custom workspace roles
        val customClaims = extractCustomClaims(jwt)
        customClaims.roles.forEach { orgRole ->
            // Create authority in format: ROLE_<workspace_id>_<ROLE>
            val authority = "ROLE_${orgRole.workspaceId}_${orgRole.role.toString().uppercase()}"
            authorities.add(SimpleGrantedAuthority(authority))
        }

        // Create custom principal with additional claims
        val principal = CustomJwtPrincipal(
            jwt.subject,
            jwt.getClaimAsString("email"),
            customClaims.roles
        )

        return JwtAuthenticationToken(jwt, authorities, principal.toString())
    }

    private fun extractCustomClaims(jwt: Jwt): CustomClaims {
        return try {
            when (val rolesRaw = jwt.getClaim<Any>("roles")) {
                is List<*> -> {
                    val roles = rolesRaw.mapNotNull { role ->
                        when (role) {
                            is Map<*, *> -> {
                                val orgIdStr = role["workspace_id"]?.toString()
                                val roleStr = role["role"]?.toString()
                                if (orgIdStr != null && roleStr != null) {
                                    try {
                                        WorkspaceRole(
                                            UUID.fromString(orgIdStr),
                                            WorkspaceRoles.fromString(roleStr)
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else null
                            }

                            else -> null
                        }
                    }
                    CustomClaims(roles)
                }

                else -> CustomClaims()
            }
        } catch (e: Exception) {
            CustomClaims()
        }
    }
}

data class WorkspaceRole(
    @param:JsonProperty("workspace_id")
    val workspaceId: UUID,
    val role: WorkspaceRoles
)

data class CustomClaims(
    val roles: List<WorkspaceRole> = emptyList()
)

data class CustomJwtPrincipal(
    val userId: String,
    val email: String?,
    val workspaceRoles: List<WorkspaceRole>
) {
    override fun toString(): String = userId
}
