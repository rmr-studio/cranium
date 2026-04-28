package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "riven.dev.seed")
data class DevSeedConfigurationProperties(
    val enabled: Boolean = false,
)
