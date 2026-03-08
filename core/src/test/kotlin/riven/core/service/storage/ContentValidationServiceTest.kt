package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import riven.core.enums.storage.StorageDomain
import riven.core.exceptions.ContentTypeNotAllowedException
import riven.core.exceptions.FileSizeLimitExceededException
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentValidationServiceTest {

    private val logger: KLogger = mock()
    private lateinit var service: ContentValidationService

    @BeforeEach
    fun setUp() {
        service = ContentValidationService(logger)
    }

    @Nested
    inner class DetectContentType {

        @Test
        fun `detects JPEG from magic bytes regardless of filename extension`() {
            // JPEG magic bytes: FF D8 FF
            val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) +
                ByteArray(16) // Pad to make valid enough for Tika
            val inputStream = ByteArrayInputStream(jpegBytes)

            val result = service.detectContentType(inputStream, "photo.txt")

            assertEquals("image/jpeg", result)
        }

        @Test
        fun `detects PNG from magic bytes regardless of filename extension`() {
            // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
            val pngBytes = byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
            ) + ByteArray(16)
            val inputStream = ByteArrayInputStream(pngBytes)

            val result = service.detectContentType(inputStream, "image.txt")

            assertEquals("image/png", result)
        }

        @Test
        fun `uses filename hint when magic bytes are ambiguous`() {
            // Plain text bytes with svg extension
            val svgContent = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>".toByteArray()
            val inputStream = ByteArrayInputStream(svgContent)

            val result = service.detectContentType(inputStream, "icon.svg")

            assertEquals("image/svg+xml", result)
        }
    }

    @Nested
    inner class ValidateContentType {

        @Test
        fun `does not throw for allowed content type`() {
            assertDoesNotThrow {
                service.validateContentType(StorageDomain.AVATAR, "image/png")
            }
        }

        @Test
        fun `throws ContentTypeNotAllowedException for disallowed content type`() {
            assertThrows<ContentTypeNotAllowedException> {
                service.validateContentType(StorageDomain.AVATAR, "application/pdf")
            }
        }
    }

    @Nested
    inner class ValidateFileSize {

        @Test
        fun `does not throw for file within size limit`() {
            val oneMb = 1L * 1024 * 1024
            assertDoesNotThrow {
                service.validateFileSize(StorageDomain.AVATAR, oneMb)
            }
        }

        @Test
        fun `throws FileSizeLimitExceededException for oversized file`() {
            val threeMb = 3L * 1024 * 1024
            assertThrows<FileSizeLimitExceededException> {
                service.validateFileSize(StorageDomain.AVATAR, threeMb)
            }
        }
    }

    @Nested
    inner class SanitizeSvg {

        @Test
        fun `strips script tags from SVG`() {
            val maliciousSvg = """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <script>alert('xss')</script>
                    <rect width="100" height="100"/>
                </svg>
            """.trimIndent().toByteArray()

            val sanitized = String(service.sanitizeSvg(maliciousSvg))

            assertFalse(sanitized.contains("<script"), "Sanitized SVG should not contain script tags")
            assertTrue(sanitized.contains("<rect"), "Sanitized SVG should retain non-script elements")
        }

        @Test
        fun `strips event handler attributes from SVG`() {
            val maliciousSvg = """
                <svg xmlns="http://www.w3.org/2000/svg">
                    <rect width="100" height="100" onload="alert('xss')"/>
                </svg>
            """.trimIndent().toByteArray()

            val sanitized = String(service.sanitizeSvg(maliciousSvg))

            assertFalse(sanitized.contains("onload"), "Sanitized SVG should not contain event handlers")
            assertTrue(sanitized.contains("<rect"), "Sanitized SVG should retain the rect element")
        }
    }

    @Nested
    inner class GenerateStorageKey {

        @Test
        fun `generates key in correct format`() {
            val workspaceId = UUID.randomUUID()
            val key = service.generateStorageKey(workspaceId, StorageDomain.AVATAR, "image/png")

            assertTrue(key.startsWith("$workspaceId/avatar/"), "Key should start with workspaceId/domain/")
            assertTrue(key.endsWith(".png"), "Key should end with .png extension")
        }

        @Test
        fun `generates unique keys on successive calls`() {
            val workspaceId = UUID.randomUUID()
            val key1 = service.generateStorageKey(workspaceId, StorageDomain.AVATAR, "image/jpeg")
            val key2 = service.generateStorageKey(workspaceId, StorageDomain.AVATAR, "image/jpeg")

            assertTrue(key1 != key2, "Each generated key should be unique")
        }

        @Test
        fun `derives correct extension for common MIME types`() {
            val workspaceId = UUID.randomUUID()

            val jpegKey = service.generateStorageKey(workspaceId, StorageDomain.AVATAR, "image/jpeg")
            assertTrue(jpegKey.endsWith(".jpg") || jpegKey.endsWith(".jpeg"), "JPEG key should have jpg/jpeg extension")

            val webpKey = service.generateStorageKey(workspaceId, StorageDomain.AVATAR, "image/webp")
            assertTrue(webpKey.endsWith(".webp"), "WebP key should have webp extension")
        }
    }
}
