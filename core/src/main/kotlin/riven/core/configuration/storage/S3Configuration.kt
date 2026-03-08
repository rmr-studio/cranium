package riven.core.configuration.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.net.url.Url
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * S3Client bean configuration for S3-compatible storage providers.
 *
 * Supports standard AWS S3 and S3-compatible services (MinIO, R2, Spaces)
 * via optional endpoint URL override. Only active when `storage.provider=s3`.
 */
@Configuration
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "s3")
class S3Configuration(
    private val storageConfig: StorageConfigurationProperties
) : DisposableBean {

    private var client: S3Client? = null

    @Bean
    fun s3Client(): S3Client {
        require(storageConfig.s3.accessKeyId.isNotBlank()) { "S3 access key ID must not be blank when provider is 's3'" }
        require(storageConfig.s3.secretAccessKey.isNotBlank()) { "S3 secret access key must not be blank when provider is 's3'" }
        val s3 = S3Client {
            region = storageConfig.s3.region
            endpointUrl = storageConfig.s3.endpointUrl?.let { Url.parse(it) }
            credentialsProvider = StaticS3CredentialsProvider(
                storageConfig.s3.accessKeyId,
                storageConfig.s3.secretAccessKey
            )
            forcePathStyle = storageConfig.s3.endpointUrl != null
        }
        client = s3
        return s3
    }

    override fun destroy() {
        client?.close()
    }
}

/**
 * Simple credentials provider that returns static access key credentials.
 */
private class StaticS3CredentialsProvider(
    private val accessKeyId: String,
    private val secretAccessKey: String
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials {
        return Credentials(accessKeyId = accessKeyId, secretAccessKey = secretAccessKey)
    }
}
