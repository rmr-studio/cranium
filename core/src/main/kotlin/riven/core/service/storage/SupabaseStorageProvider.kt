package riven.core.service.storage

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.oshai.kotlinlogging.KLogger
import io.ktor.http.ContentType
import jakarta.annotation.PostConstruct
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
 * Supabase Storage implementation of StorageProvider.
 *
 * Delegates all operations to the Supabase Storage API via supabase-kt.
 * Suspend calls are bridged to blocking via `runBlocking {}`.
 * Only active when `storage.provider=supabase`.
 */
@Service
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "supabase")
class SupabaseStorageProvider(
    private val logger: KLogger,
    private val supabaseClient: SupabaseClient,
    private val storageConfig: StorageConfigurationProperties
) : StorageProvider {

    private val bucketName: String
        get() = storageConfig.supabase.bucket

    /** Access the Storage plugin. Protected for testability -- supabase-kt extension is static. */
    protected open fun storagePlugin(): Storage = supabaseClient.storage

    private val bucket: BucketApi
        get() = storagePlugin().from(bucketName)

    // ------ Lifecycle ------

    /**
     * Probe the configured bucket at startup and best-effort create it if missing.
     *
     * Bucket creation requires a service-role key. When the configured key lacks admin
     * permissions (e.g. anon key) creation will fail with an RLS violation — in that case
     * the bucket must be created manually in the Supabase dashboard.
     *
     * This method never throws: a misconfigured bucket should not prevent the application
     * from booting. Upload calls will surface the underlying error at request time.
     */
    @PostConstruct
    fun ensureBucketExists() {
        try {
            runBlocking { storagePlugin().retrieveBucketById(bucketName) }
            return
        } catch (e: Exception) {
            if (!isNotFound(e)) {
                logger.warn(e) { "Failed to probe Supabase bucket '$bucketName'; storage operations may fail" }
                return
            }
        }

        try {
            logger.info { "Bucket '$bucketName' not found, attempting to create it" }
            runBlocking {
                storagePlugin().createBucket(id = bucketName) { public = false }
            }
        } catch (e: Exception) {
            logger.warn(e) {
                "Failed to auto-create Supabase bucket '$bucketName'. " +
                    "Create it manually in the Supabase dashboard, or configure a service-role key."
            }
        }
    }

    // ------ Public Operations ------

    /**
     * Upload a file to Supabase Storage.
     *
     * Uses upsert mode to overwrite existing files with the same key.
     */
    override fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult {
        try {
            val bytes = content.readAllBytes()
            runBlocking {
                bucket.upload(key, bytes) {
                    upsert = true
                    this.contentType = parseContentType(contentType)
                }
            }
            logger.debug { "Uploaded file to Supabase Storage: $key ($contentLength bytes)" }
            return StorageResult(storageKey = key, contentType = contentType, contentLength = contentLength)
        } catch (e: Exception) {
            throw StorageProviderException("Upload failed for key: $key", e)
        }
    }

    /**
     * Download a file from Supabase Storage.
     *
     * Returns the file content as a ByteArrayInputStream. Content type is not available
     * from the download response, so defaults to "application/octet-stream".
     *
     * @throws StorageNotFoundException if the file does not exist
     */
    override fun download(key: String): DownloadResult {
        try {
            val bytes = runBlocking { bucket.downloadAuthenticated(key) }
            return DownloadResult(
                content = ByteArrayInputStream(bytes),
                contentType = "application/octet-stream",
                contentLength = bytes.size.toLong(),
                originalFilename = null
            )
        } catch (e: Exception) {
            if (isNotFound(e)) throw StorageNotFoundException("File not found: $key")
            throw StorageProviderException("Download failed for key: $key", e)
        }
    }

    /**
     * Delete a file from Supabase Storage. Idempotent -- does not throw on missing file.
     */
    override fun delete(key: String) {
        try {
            runBlocking { bucket.delete(key) }
            logger.debug { "Deleted file from Supabase Storage: $key" }
        } catch (e: Exception) {
            throw StorageProviderException("Delete failed for key: $key", e)
        }
    }

    /**
     * Check whether a file exists in Supabase Storage.
     */
    override fun exists(key: String): Boolean {
        return try {
            runBlocking { bucket.exists(key) }
        } catch (e: Exception) {
            throw StorageProviderException("Existence check failed for key: $key", e)
        }
    }

    /**
     * Generate a time-limited presigned URL for uploading a file directly to Supabase Storage.
     *
     * Uses createSignedUploadUrl which returns an UploadSignedUrl containing the full URL.
     * Note: Supabase SDK does not support custom TTL on upload URLs — server-side default is used.
     */
    override fun generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String {
        if (expiresIn != Duration.ofSeconds(900)) {
            logger.warn { "Supabase provider does not support custom TTL for presigned upload URLs; using server default" }
        }
        try {
            val uploadSignedUrl = runBlocking {
                bucket.createSignedUploadUrl(path = key, upsert = false)
            }
            return uploadSignedUrl.url
        } catch (e: Exception) {
            throw StorageProviderException("Upload URL generation failed for key: $key", e)
        }
    }

    /**
     * Generate a time-limited signed URL for downloading a file directly from Supabase.
     */
    override fun generateSignedUrl(key: String, expiresIn: Duration): String {
        try {
            return runBlocking {
                bucket.createSignedUrl(path = key, expiresIn = expiresIn.toKotlinDuration())
            }
        } catch (e: Exception) {
            throw StorageProviderException("Signed URL generation failed for key: $key", e)
        }
    }

    /**
     * Check Supabase Storage health by verifying the bucket exists.
     *
     * Bucket creation is handled at startup via [ensureBucketExists].
     */
    override fun healthCheck(): Boolean {
        return try {
            runBlocking { storagePlugin().retrieveBucketById(bucketName) }
            true
        } catch (e: Exception) {
            logger.warn { "Supabase storage health check failed: ${e.message}" }
            false
        }
    }

    // ------ Private Helpers ------

    /**
     * Check if an exception indicates a not-found condition from Supabase.
     */
    private fun isNotFound(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: return false
        return message.contains("not found") || message.contains("404") || message.contains("object not found")
    }

    /**
     * Parse a MIME type string into a Ktor ContentType.
     */
    private fun parseContentType(mimeType: String): ContentType {
        return try {
            ContentType.parse(mimeType)
        } catch (e: Exception) {
            ContentType.Application.OctetStream
        }
    }
}
