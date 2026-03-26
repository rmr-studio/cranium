package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "riven.nango")
data class NangoConfigurationProperties(
    val secretKey: String = "",
    val baseUrl: String = "https://api.nango.dev",
    val maxWebhookBodySize: Int = 1_048_576,
)
