package cranium.core.configuration.properties

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "cranium.query")
data class QueryConfigurationProperties(
    @field:Min(1)
    @field:Max(Int.MAX_VALUE.toLong())
    val timeoutSeconds: Long = 10
)
