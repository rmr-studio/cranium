package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "riven.query")
data class QueryConfigurationProperties(
    val timeoutSeconds: Long = 10
)
