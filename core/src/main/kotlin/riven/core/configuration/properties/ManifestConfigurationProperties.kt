package cranium.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cranium.manifests")
data class ManifestConfigurationProperties(
    val autoLoad: Boolean = true,
    val basePath: String = "classpath:manifests"
)
