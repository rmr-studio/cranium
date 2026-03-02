package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "riven.posthog")
data class PostHogConfigurationProperties(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val host: String = "https://us.i.posthog.com"
)
