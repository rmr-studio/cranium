package cranium.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cranium.nango")
data class NangoConfigurationProperties(
    val secretKey: String = "",
    val baseUrl: String = "https://api.nango.dev",
    val maxWebhookBodySize: Int = 1_048_576,
)
