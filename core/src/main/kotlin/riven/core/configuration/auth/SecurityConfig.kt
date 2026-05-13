package cranium.core.configuration.auth

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import cranium.core.configuration.properties.SecurityConfigurationProperties
import cranium.core.configuration.properties.WebSocketConfigurationProperties
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val securityConfig: SecurityConfigurationProperties,
    private val tokenDecoder: CustomAuthenticationTokenConverter,
    private val wsProperties: WebSocketConfigurationProperties,
) {

    private val secretKey = SecretKeySpec(securityConfig.jwtSecretKey.toByteArray(Charsets.UTF_8), "HmacSHA256")

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            cors { configurationSource = corsConfig() }
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/api/auth/**", permitAll)
                authorize("/actuator/**", permitAll)
                authorize("/docs/**", permitAll)
                authorize("/public/**", permitAll)
                authorize("/api/v1/webhooks/nango", permitAll)
                authorize("/api/v1/storage/download/{token}", permitAll)
                authorize("/api/v1/avatars/**", permitAll)
                authorize("${wsProperties.endpoint}/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2ResourceServer {
                jwt { jwtAuthenticationConverter = tokenDecoder }
            }
            exceptionHandling {
                authenticationEntryPoint = AuthenticationEntryPoint { _, response, authException ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.message)
                }
                accessDeniedHandler = AccessDeniedHandler { _, response, accessDeniedException ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedException.message)
                }
            }
        }

        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withSecretKey(secretKey).build()
    }

    @Bean
    fun corsConfig(): CorsConfigurationSource {
        val corsConfig = CorsConfiguration()
        corsConfig.allowedOrigins = securityConfig.allowedOrigins
        corsConfig.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        corsConfig.allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "Origin")
        corsConfig.exposedHeaders = listOf(
            "Authorization",
            "Content-Type",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "Retry-After",
        )
        corsConfig.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConfig)
        return source
    }

}
