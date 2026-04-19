package riven.core.service.util

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import riven.core.enums.workspace.WorkspaceRoles
import java.lang.annotation.Inherited
import java.time.Instant
import java.util.*

object JwtTestUtil {

    /**
     * Creates a JWT similar to Supabase's format for testing.
     *
     * @param id The user ID (sub claim).
     * @param email The user's email.
     * @param displayName Optional display name (stored in user_metadata).
     * @param roles Optional list of roles (stored in app_metadata).
     * @param customClaims Optional map of additional claims.
     * @param expirationSeconds Duration until the JWT expires (default: 3600).
     * @param issuer The issuer (default: Supabase auth URL).
     * @return The encoded JWT as a String.
     */
    fun createTestJwt(
        id: String,
        email: String,
        displayName: String? = null,
        roles: List<WorkspaceRole> = emptyList(),
        customClaims: Map<String, Any> = emptyMap(),
        expirationSeconds: Long = 3600,
        issuer: String = "https://abc.supabase.co/auth/v1",
        secret: String = "test-secret-1234567890abcdef1234567890abcdef"

    ): String {
        try {
            val header = JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .build()

            val now = Instant.now()
            val claimsBuilder = JWTClaimsSet.Builder()
                .subject(id)
                .issuer(issuer)
                .audience("authenticated")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(expirationSeconds)))
                .claim("email", email)
                .claim("role", "authenticated")

            if (displayName != null) {
                claimsBuilder.claim("user_metadata", mapOf("displayName" to displayName))
            }

            if (roles.isNotEmpty()) {
                claimsBuilder.claim("app_metadata", mapOf("roles" to roles.map {
                    mapOf(
                        "workspace_id" to it.workspaceId,
                        "role" to it.role
                    )
                }))
            }

            customClaims.forEach { (k, v) -> claimsBuilder.claim(k, v) }

            val claims = claimsBuilder.build()

            val signedJWT = SignedJWT(header, claims)
            val signer = MACSigner(secret.toByteArray(Charsets.UTF_8))
            signedJWT.sign(signer)

            val jwt = signedJWT.serialize()
            return jwt
        } catch (e: Exception) {
            throw e
        }
    }
}

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WorkspaceRole(val workspaceId: String, val role: WorkspaceRoles)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
@ExtendWith(WithUserPersonaExtension::class)
annotation class WithUserPersona(
    val userId: String,
    val email: String,
    val displayName: String = "",
    val roles: Array<WorkspaceRole> = [],
    val expirationSeconds: Long = 3600
)

class WithUserPersonaExtension : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        val annotation = findEffectiveAnnotation(context)

        if (annotation != null) {
            val jwtString = JwtTestUtil.createTestJwt(
                id = annotation.userId,
                email = annotation.email,
                displayName = annotation.displayName.ifEmpty { null },
                roles = annotation.roles.toList(),
                expirationSeconds = annotation.expirationSeconds
            )

            // Create a Spring Security Jwt object
            val claims = mutableMapOf<String, Any>(
                "sub" to annotation.userId,
                "email" to annotation.email,
                "role" to "authenticated",
                "iss" to "https://abc.supabase.co/auth/v1",
                "aud" to "authenticated"
            )
            if (annotation.displayName.isNotEmpty()) {
                claims["user_metadata"] = mapOf("displayName" to annotation.displayName)
            }
            if (annotation.roles.isNotEmpty()) {
                claims["app_metadata"] = mapOf("roles" to annotation.roles.toList())
            }

            val jwt = Jwt(
                jwtString,
                Instant.now(),
                Instant.now().plusSeconds(annotation.expirationSeconds),
                mapOf("alg" to "HS256", "typ" to "JWT"),
                claims
            )

            // Set JwtAuthenticationToken in SecurityContext
            val authorities = annotation.roles.map {
                SimpleGrantedAuthority("ROLE_${it.workspaceId}_${it.role.toString().uppercase(Locale.ROOT)}")
            }
            val auth = JwtAuthenticationToken(jwt, authorities)
            SecurityContextHolder.getContext().authentication = auth
        }
    }

    override fun afterEach(context: ExtensionContext) {
        SecurityContextHolder.clearContext()
    }

    private fun findEffectiveAnnotation(context: ExtensionContext): WithUserPersona? {
        val methodAnnotation = context.testMethod
            .map { it.getAnnotation(WithUserPersona::class.java) }
            .orElse(null)

        if (methodAnnotation != null) {
            return methodAnnotation
        }

        return context.testClass
            .map { it.getAnnotation(WithUserPersona::class.java) }
            .orElse(null)
    }
}