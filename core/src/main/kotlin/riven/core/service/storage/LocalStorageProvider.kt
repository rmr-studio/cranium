package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import riven.core.configuration.storage.StorageConfigurationProperties
import riven.core.exceptions.StorageNotFoundException
import riven.core.exceptions.StorageProviderException
import riven.core.models.storage.DownloadResult
import riven.core.models.storage.StorageProvider
import riven.core.models.storage.StorageResult
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Local filesystem implementation of StorageProvider.
 *
 * Stores files under `{basePath}/{key}` where key follows the format
 * `{workspaceId}/{domain}/{uuid}.{ext}`. Only active when `storage.provider=local`.
 */
@Service
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "local")
class LocalStorageProvider(
    private val logger: KLogger,
    private val storageConfig: StorageConfigurationProperties
) : StorageProvider {

    private val basePath: Path
        get() = Paths.get(storageConfig.local.basePath).toAbsolutePath().normalize()

    // ------ Public Operations ------

    /**
     * Upload a file to the local filesystem.
     *
     * Creates parent directories if they do not exist. Returns a StorageResult
     * with the storage key and metadata.
     */
    override fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult {
        val path = resolveAndValidate(key)
        Files.createDirectories(path.parent)
        Files.copy(content, path, StandardCopyOption.REPLACE_EXISTING)
        logger.debug { "Uploaded file to local storage: $key ($contentLength bytes)" }
        return StorageResult(
            storageKey = key,
            contentType = contentType,
            contentLength = contentLength
        )
    }

    /**
     * Download a file from the local filesystem.
     *
     * @throws StorageNotFoundException if the file does not exist
     */
    override fun download(key: String): DownloadResult {
        val path = resolveAndValidate(key)
        if (!Files.exists(path)) {
            throw StorageNotFoundException("File not found: $key")
        }

        val contentType = Files.probeContentType(path) ?: "application/octet-stream"
        val contentLength = Files.size(path)

        logger.debug { "Downloading file from local storage: $key ($contentLength bytes)" }
        return DownloadResult(
            content = Files.newInputStream(path),
            contentType = contentType,
            contentLength = contentLength,
            originalFilename = null
        )
    }

    /**
     * Delete a file from the local filesystem. Idempotent -- no error if file is already absent.
     */
    override fun delete(key: String) {
        val path = resolveAndValidate(key)
        val deleted = Files.deleteIfExists(path)
        if (deleted) {
            logger.debug { "Deleted file from local storage: $key" }
        } else {
            logger.debug { "File already absent from local storage: $key" }
        }
    }

    /** Check whether a file exists in the local filesystem. */
    override fun exists(key: String): Boolean {
        val path = resolveAndValidate(key)
        return Files.exists(path)
    }

    /**
     * Not supported by the local adapter.
     *
     * Presigned upload URLs require a remote storage backend. Local storage
     * uses proxied upload via StorageService instead.
     *
     * @throws UnsupportedOperationException always
     */
    override fun generateUploadUrl(key: String, contentType: String, expiresIn: Duration): String {
        throw UnsupportedOperationException("Local storage does not support presigned upload URLs")
    }

    /**
     * Not supported by the local adapter.
     *
     * Signed URL generation for local storage is handled by SignedUrlService,
     * which is wired through StorageService (Plan 03).
     *
     * @throws UnsupportedOperationException always
     */
    override fun generateSignedUrl(key: String, expiresIn: Duration): String {
        throw UnsupportedOperationException("Use SignedUrlService for local signed URLs")
    }

    /** Check whether the base storage directory is writable. */
    override fun healthCheck(): Boolean {
        return try {
            Files.isWritable(basePath)
        } catch (e: Exception) {
            logger.warn { "Local storage health check failed: ${e.message}" }
            false
        }
    }

    // ------ Private Helpers ------

    /**
     * Resolve a storage key against the base path with path traversal prevention.
     *
     * @throws StorageProviderException if the resolved path escapes the base directory
     */
    private fun resolveAndValidate(key: String): Path {
        val resolved = basePath.resolve(key).normalize()
        if (!resolved.startsWith(basePath)) {
            throw StorageProviderException("Path traversal detected in storage key: $key", null)
        }
        return resolved
    }
}
