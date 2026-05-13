package cranium.core.configuration.properties

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "cranium.rate-limit")
data class RateLimitConfigurationProperties(
    val enabled: Boolean = true,
    @field:Min(1) val authenticatedRpm: Long = 200,
    @field:Min(1) val anonymousRpm: Long = 30,
    @field:Min(1) val cacheMaxSize: Long = 10_000,
    @field:Min(1) val cacheExpireMinutes: Long = 10,
    val trustedProxyCidrs: List<String> = emptyList(),
)
