package cranium.core.configuration.workflow

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("cranium.workflow.engine")
data class TemporalEngineConfigurationProperties(
    val target: String,
    val namespace: String? = null,
)