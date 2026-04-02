package riven.core.service.auth

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
open class AuthTokenService(private val logger: KLogger) {

    /**
     * Retrieves the JWT from the security context.
     */
    private fun getJwt(): Jwt {
        val authentication = SecurityContextHolder.getContext().authentication
        authentication.let {
            if (it == null || it.principal !is Jwt) {
                logger.warn { "No JWT found in the security context" }
                throw AccessDeniedException("No JWT found in the security context")
            }

            return it.principal as Jwt
        }
    }

    /**
     * Retrieves the user ID from the JWT claims.
     */
    @Throws(AccessDeniedException::class, IllegalArgumentException::class)
    open fun getUserId(): UUID {
        return getJwt().claims["sub"].let {
            if (it == null) {
                logger.warn { "User ID not found in JWT claims" }
                throw AccessDeniedException("User ID not found in JWT claims")
            } else {
                UUID.fromString(it.toString())
            }
        }
    }


    /**
     * Retrieves the user's display name from the JWT user_metadata claim.
     * Falls back to email if no name is available.
     */
    open fun getUserDisplayName(): String {
        val jwt = getJwt()
        val metadata = jwt.claims["user_metadata"]
        if (metadata is Map<*, *>) {
            val name = metadata["full_name"] ?: metadata["name"]
            if (name != null) return name.toString()
        }
        return jwt.claims["email"]?.toString() ?: "Unknown"
    }

    /**
     * Safe alternative to [getUserDisplayName] for contexts where a security context
     * may not exist (e.g. async event handlers, background tasks).
     * Returns null instead of throwing when no JWT is present.
     */
    open fun getUserDisplayNameOrNull(): String? {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return null
        val jwt = authentication.principal as? Jwt ?: return null
        val metadata = jwt.claims["user_metadata"]
        if (metadata is Map<*, *>) {
            val name = metadata["full_name"] ?: metadata["name"]
            if (name != null) return name.toString()
        }
        return jwt.claims["email"]?.toString()
    }

    open fun getUserEmail(): String {
        return getJwt().claims["email"].let {
            if (it == null) {
                logger.warn { "Email not found in JWT claims" }
                throw AccessDeniedException("Email not found in JWT claims")
            } else {
                it.toString()
            }
        }
    }

    /**
     * Retrieves all associated user metadata from the JWT Claim
     */
    fun getAllClaims(): Map<String, Any> {
        return getJwt().claims
            .also { logger.info { "Retrieved claims: $it" } }
    }

    fun getCurrentUserAuthorities(): Collection<String> {
        return SecurityContextHolder.getContext().authentication?.authorities
            ?.map { it.authority } ?: emptyList()
    }

}