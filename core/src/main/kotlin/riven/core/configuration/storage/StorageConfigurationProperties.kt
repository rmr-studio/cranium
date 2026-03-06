package riven.core.configuration.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "storage")
@Validated
data class StorageConfigurationProperties(
    val provider: String = "local",
    val local: Local = Local(),
    val signedUrl: SignedUrl = SignedUrl(),
    val presignedUpload: PresignedUpload = PresignedUpload(),
    val supabase: Supabase = Supabase(),
    val s3: S3 = S3()
) {
    data class Local(
        val basePath: String = "./storage"
    )

    data class SignedUrl(
        val secret: String = "dev-secret-change-in-production",
        val defaultExpirySeconds: Long = 3600,
        val maxExpirySeconds: Long = 86400
    )

    data class PresignedUpload(
        val expirySeconds: Long = 900
    )

    data class Supabase(
        val bucket: String = "riven-storage"
    )

    data class S3(
        val bucket: String = "riven-storage",
        val region: String = "us-east-1",
        val accessKeyId: String = "",
        val secretAccessKey: String = "",
        val endpointUrl: String? = null
    )
}
