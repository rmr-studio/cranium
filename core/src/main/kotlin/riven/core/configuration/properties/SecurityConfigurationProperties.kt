package cranium.core.configuration.properties

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cranium.security")
data class SecurityConfigurationProperties(
    @field:NotBlank(message = "JWT secret key must not be blank")
    @field:Size(min = 32, message = "JWT secret key must be at least 32 characters long")
    val jwtSecretKey: String,

    @field:NotBlank(message = "JWT issuer must not be blank")
    val jwtIssuer: String,

    @field:NotEmpty(message = "Allowed origins must not be empty")
    val allowedOrigins: List<String>,
)