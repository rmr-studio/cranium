package cranium.core.configuration.auth

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.*

class SecurityAuditorAware : AuditorAware<UUID> {
    /**
     * Resolves the current auditor as a UUID from the Spring Security context.
     *
     * Returns Optional.empty() when there is no authentication, the principal is not a Jwt, or the authentication is not authenticated.
     * When present, the method reads the JWT `sub` claim and converts it to a UUID, returning that UUID wrapped in an Optional.
     *
     * @return an Optional containing the current auditor's UUID when available
     */
    override fun getCurrentAuditor(): Optional<UUID> {
        val auth = SecurityContextHolder.getContext().authentication ?: return Optional.empty()
        if (!auth.isAuthenticated) return Optional.empty()
        val jwt = (auth.principal as? Jwt) ?: return Optional.empty()
        val sub = jwt.subject ?: jwt.claims["sub"]?.toString() ?: return Optional.empty()
        return try {
            Optional.of(UUID.fromString(sub))
        } catch (_: IllegalArgumentException) {
            Optional.empty()
        }
    }
}