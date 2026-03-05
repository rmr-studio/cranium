package riven.core.models.storage

import java.io.InputStream
import java.time.Duration

/**
 * Abstraction for file storage operations.
 *
 * Each implementation is activated via `@ConditionalOnProperty("storage.provider")`.
 * Only one provider bean is active at runtime.
 *
 * All methods are blocking (non-suspend) to match the synchronous Spring MVC codebase.
 */
interface StorageProvider {

    /**
     * Upload a file to the storage backend.
     *
     * @param key storage key in the format `{workspaceId}/{domain}/{uuid}.{ext}`
     * @param content file content stream
     * @param contentType MIME type of the file
     * @param contentLength size of the file in bytes
     * @return result containing the storage key and metadata
     */
    fun upload(key: String, content: InputStream, contentType: String, contentLength: Long): StorageResult

    /**
     * Download a file from the storage backend.
     *
     * @param key storage key identifying the file
     * @return result containing the file content stream and metadata
     */
    fun download(key: String): DownloadResult

    /**
     * Delete a file from the storage backend.
     *
     * @param key storage key identifying the file
     */
    fun delete(key: String)

    /**
     * Check whether a file exists in the storage backend.
     *
     * @param key storage key identifying the file
     * @return true if the file exists
     */
    fun exists(key: String): Boolean

    /**
     * Generate a time-limited signed URL for downloading a file.
     *
     * @param key storage key identifying the file
     * @param expiresIn duration until the signed URL expires
     * @return signed URL string
     */
    fun generateSignedUrl(key: String, expiresIn: Duration): String

    /**
     * Check whether the storage backend is healthy and reachable.
     *
     * @return true if the backend is operational
     */
    fun healthCheck(): Boolean
}
