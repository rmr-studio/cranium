package riven.core.configuration.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "storage")
@Validated
data class StorageConfigurationProperties(
    val provider: String = "local",
    val local: Local = Local(),
    val signedUrl: SignedUrl = SignedUrl()
) {
    data class Local(
        val basePath: String = "./storage"
    )

    data class SignedUrl(
        val secret: String = "dev-secret-change-in-production",
        val defaultExpirySeconds: Long = 3600,
        val maxExpirySeconds: Long = 86400
    )
}
