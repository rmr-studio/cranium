package cranium.core.configuration.properties

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "cranium")
@Validated
data class ApplicationConfigurationProperties(
    val includeStackTrace: Boolean = true,
    @field:NotBlank
    val supabaseUrl: String,
    @field:NotBlank
    val supabaseKey: String,
    @field:NotBlank
    val webOrigin: String = "http://localhost:3000", // Default to localhost for development purposes
)
