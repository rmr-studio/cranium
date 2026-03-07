package riven.core.service.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import riven.core.configuration.storage.StorageConfigurationProperties
import riven.core.exceptions.StorageNotFoundException
import riven.core.exceptions.StorageProviderException
import riven.core.models.storage.DownloadResult
import riven.core.models.storage.StorageProvider
import riven.core.models.storage.StorageResult
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Duration
import kotlin.time.toKotlinDuration

/**
 * S3-compatible storage implementation of StorageProvider.
 *
 * Supports standard AWS S3 and S3-compatible services (MinIO, R2, Spaces)
 * via endpoint URL override in configuration. Suspend calls are bridged to
 * blocking via `runBlocking {}`. Only active when `storage.provider=s3`.
 */
@Service
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "s3")
class S3StorageProvider(
    private val logger: KLogger,
    private val s3Client: S3Client,
    private val storageConfig: StorageConfigurationProperties
) : StorageProvider {

    private val bucketName: String
        get() = storageConfig.s3.bucket

    // ------ Public Operations ------

    /**
     * Upload a file to S3.
     *
     * Reads the input stream into memory and uploads via putObject.
     */
    override fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult {
        try {
            val bytes = content.readAllBytes()
            val request = PutObjectRequest {
                bucket = bucketName
                this.key = key
                this.contentType = contentType
                this.contentLength = contentLength
                body = ByteStream.fromBytes(bytes)
            }
            runBlocking { s3Client.putObject(request) }
            logger.debug { "Uploaded file to S3: $key ($contentLength bytes)" }
            return StorageResult(storageKey = key, contentType = contentType, contentLength = contentLength)
        } catch (e: Exception) {
            throw StorageProviderException("Upload failed for key: $key", e)
        }
    }

    /**
     * Download a file from S3.
     *
     * Body is consumed inside the getObject lambda to avoid closed-stream issues.
     *
     * @throws StorageNotFoundException if the key does not exist
     */
    override fun download(key: String): DownloadResult {
        try {
            val request = GetObjectRequest {
                bucket = bucketName
                this.key = key
            }
            val result = runBlocking {
                s3Client.getObject(request) { response ->
                    val bytes = response.body?.toByteArray() ?: ByteArray(0)
                    Triple(
                        bytes,
                        response.contentType ?: "application/octet-stream",
                        response.contentLength ?: 0L
                    )
                }
            }
            return DownloadResult(
                content = ByteArrayInputStream(result.first),
                contentType = result.second,
                contentLength = result.third,
                originalFilename = null
            )
        } catch (e: Exception) {
            if (isNotFound(e)) throw StorageNotFoundException("File not found: $key")
            throw StorageProviderException("Download failed for key: $key", e)
        }
    }

    /**
     * Delete a file from S3. Idempotent -- S3 deleteObject does not error on missing keys.
     */
    override fun delete(key: String) {
        try {
            val request = DeleteObjectRequest {
                bucket = bucketName
                this.key = key
            }
            runBlocking { s3Client.deleteObject(request) }
            logger.debug { "Deleted file from S3: $key" }
        } catch (e: Exception) {
            throw StorageProviderException("Delete failed for key: $key", e)
        }
    }

    /**
     * Check whether a file exists in S3 via headObject.
     */
    override fun exists(key: String): Boolean {
        return try {
            val request = HeadObjectRequest {
                bucket = bucketName
                this.key = key
            }
            runBlocking { s3Client.headObject(request) }
            true
        } catch (e: Exception) {
            if (isNotFound(e)) return false
            throw StorageProviderException("Existence check failed for key: $key", e)
        }
    }

    /**
     * Generate a presigned PUT URL for direct upload to S3.
     *
     * Intentionally omits contentType from the PutObjectRequest to avoid S3 403 errors
     * when the client uploads with a different Content-Type than what was signed.
     * Content type validation is handled post-upload via Tika detection.
     */
    override fun generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String {
        try {
            val request = PutObjectRequest {
                bucket = bucketName
                this.key = key
            }
            val presigned = runBlocking {
                s3Client.presignPutObject(request, expiresIn.toKotlinDuration())
            }
            return presigned.url.toString()
        } catch (e: Exception) {
            throw StorageProviderException("Upload URL generation failed for key: $key", e)
        }
    }

    /**
     * Generate a presigned GET URL for direct download from S3.
     */
    override fun generateSignedUrl(key: String, expiresIn: Duration): String {
        try {
            val request = GetObjectRequest {
                bucket = bucketName
                this.key = key
            }
            val presigned = runBlocking {
                s3Client.presignGetObject(request, expiresIn.toKotlinDuration())
            }
            return presigned.url.toString()
        } catch (e: Exception) {
            throw StorageProviderException("Signed URL generation failed for key: $key", e)
        }
    }

    /**
     * Check S3 health by verifying the bucket exists.
     *
     * Auto-creates the bucket if it does not exist.
     */
    override fun healthCheck(): Boolean {
        return try {
            runBlocking {
                try {
                    s3Client.headBucket(HeadBucketRequest { bucket = bucketName })
                } catch (e: S3Exception) {
                    if (e is NotFound || e is NoSuchBucket) {
                        logger.info { "Bucket '$bucketName' not found, creating it" }
                        s3Client.createBucket(CreateBucketRequest { bucket = bucketName })
                    } else {
                        throw e
                    }
                }
            }
            true
        } catch (e: Exception) {
            logger.warn { "S3 storage health check failed: ${e.message}" }
            false
        }
    }

    // ------ Private Helpers ------

    /**
     * Check if an exception indicates a not-found condition from S3.
     */
    private fun isNotFound(e: Exception): Boolean {
        if (e is NoSuchKey || e is NotFound) return true
        val message = e.message?.lowercase() ?: return false
        return message.contains("nosuchkey") || message.contains("404") || message.contains("not found")
    }
}
