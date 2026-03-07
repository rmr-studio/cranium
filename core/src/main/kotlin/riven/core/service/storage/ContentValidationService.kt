package riven.core.service.storage

import io.github.borewit.sanitize.SVGSanitizer
import io.github.oshai.kotlinlogging.KLogger
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.mime.MimeTypes
import org.springframework.stereotype.Service
import riven.core.enums.storage.StorageDomain
import riven.core.exceptions.ContentTypeNotAllowedException
import riven.core.exceptions.FileSizeLimitExceededException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Validates file content before storage operations.
 *
 * Provides MIME type detection via Apache Tika magic bytes,
 * content type and file size validation against domain rules,
 * SVG sanitization, and storage key generation.
 */
@Service
class ContentValidationService(
    private val logger: KLogger
) {

    private val tika = Tika()
    private val mimeTypes = MimeTypes.getDefaultMimeTypes()

    // ------ MIME Detection ------

    /**
     * Detect MIME type from stream content using magic bytes.
     *
     * Uses Apache Tika to detect from byte signatures, not file extension.
     * The filename is used only as a hint when magic bytes are ambiguous.
     *
     * @param inputStream the file content stream (will be consumed)
     * @param filename optional original filename for hint
     * @return detected MIME type string
     */
    fun detectContentType(inputStream: InputStream, filename: String?): String {
        val metadata = Metadata()
        if (filename != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename)
        }

        val detectedType = tika.detect(inputStream, metadata)
        logger.debug { "Detected content type: $detectedType for filename: $filename" }
        return detectedType
    }

    // ------ Validation ------

    /**
     * Validate that the content type is allowed for the given storage domain.
     *
     * @throws ContentTypeNotAllowedException if the content type is not in the domain's allowlist
     */
    fun validateContentType(domain: StorageDomain, contentType: String) {
        if (!domain.isContentTypeAllowed(contentType)) {
            throw ContentTypeNotAllowedException(
                "Content type '$contentType' is not allowed for domain ${domain.name}. " +
                    "Allowed types: ${domain.allowedContentTypes}"
            )
        }
    }

    /**
     * Validate that the file size is within the domain's limit.
     *
     * @throws FileSizeLimitExceededException if the file exceeds the domain's max size
     */
    fun validateFileSize(domain: StorageDomain, fileSize: Long) {
        if (!domain.isFileSizeAllowed(fileSize)) {
            throw FileSizeLimitExceededException(
                "File size ${fileSize} bytes exceeds maximum ${domain.maxFileSize} bytes for domain ${domain.name}"
            )
        }
    }

    // ------ SVG Sanitization ------

    /**
     * Sanitize SVG content by stripping script tags, event handlers, and embedded JS.
     *
     * @param input raw SVG bytes
     * @return sanitized SVG bytes
     */
    fun sanitizeSvg(input: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(input)
        val outputStream = ByteArrayOutputStream()

        SVGSanitizer.sanitize(inputStream, outputStream)

        val sanitized = outputStream.toByteArray()
        logger.debug { "SVG sanitized: ${input.size} bytes -> ${sanitized.size} bytes" }
        return sanitized
    }

    // ------ Storage Key Generation ------

    /**
     * Generate a UUID-based storage key for the given workspace and domain.
     *
     * Format: `{workspaceId}/{domain}/{uuid}.{ext}`
     * Extension is derived from the MIME type using Tika's MIME type registry.
     *
     * @param workspaceId workspace UUID
     * @param domain storage domain
     * @param contentType detected MIME type
     * @return storage key string
     */
    fun generateStorageKey(workspaceId: UUID, domain: StorageDomain, contentType: String): String {
        val extension = deriveExtension(contentType)
        val uuid = UUID.randomUUID()
        val domainPath = domain.name.lowercase()
        return "$workspaceId/$domainPath/$uuid$extension"
    }

    /**
     * Generate a UUID-based storage key scoped to a user instead of a workspace.
     *
     * Format: `users/{userId}/{domain}/{uuid}.{ext}`
     *
     * @param userId user UUID
     * @param domain storage domain
     * @param contentType detected MIME type
     * @return storage key string
     */
    fun generateUserStorageKey(userId: UUID, domain: StorageDomain, contentType: String): String {
        val extension = deriveExtension(contentType)
        val uuid = UUID.randomUUID()
        val domainPath = domain.name.lowercase()
        return "users/$userId/$domainPath/$uuid$extension"
    }

    // ------ Private Helpers ------

    private fun deriveExtension(contentType: String): String {
        return try {
            val mimeType = mimeTypes.forName(contentType)
            mimeType.extension // Tika returns with leading dot (e.g. ".png")
        } catch (e: Exception) {
            logger.warn { "Could not derive extension for MIME type '$contentType': ${e.message}" }
            ""
        }
    }
}
