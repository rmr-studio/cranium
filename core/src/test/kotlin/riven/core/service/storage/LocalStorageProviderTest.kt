package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import riven.core.configuration.storage.StorageConfigurationProperties
import riven.core.exceptions.StorageNotFoundException
import riven.core.exceptions.StorageProviderException
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalStorageProviderTest {

    @TempDir
    lateinit var tempDir: Path

    private val logger: KLogger = mock()
    private lateinit var provider: LocalStorageProvider

    @BeforeEach
    fun setUp() {
        val config = StorageConfigurationProperties(
            provider = "local",
            local = StorageConfigurationProperties.Local(basePath = tempDir.toString())
        )
        provider = LocalStorageProvider(logger, config)
    }

    @Nested
    inner class Upload {

        @Test
        fun `writes file and returns StorageResult with correct metadata`() {
            val content = "hello world".toByteArray()
            val key = "workspace1/avatar/abc123.png"

            val result = provider.upload(
                key = key,
                content = ByteArrayInputStream(content),
                contentType = "image/png",
                contentLength = content.size.toLong()
            )

            assertEquals(key, result.storageKey)
            assertEquals("image/png", result.contentType)
            assertEquals(content.size.toLong(), result.contentLength)

            val filePath = tempDir.resolve(key)
            assertTrue(Files.exists(filePath), "File should exist on disk")
            assertEquals("hello world", Files.readString(filePath))
        }

        @Test
        fun `creates parent directories if they do not exist`() {
            val content = "test".toByteArray()
            val key = "deep/nested/path/file.txt"

            provider.upload(
                key = key,
                content = ByteArrayInputStream(content),
                contentType = "text/plain",
                contentLength = content.size.toLong()
            )

            val filePath = tempDir.resolve(key)
            assertTrue(Files.exists(filePath), "File should exist in nested directory")
        }
    }

    @Nested
    inner class Download {

        @Test
        fun `returns DownloadResult with file content and metadata`() {
            val content = "download me".toByteArray()
            val key = "workspace1/avatar/file.png"
            val filePath = tempDir.resolve(key)
            Files.createDirectories(filePath.parent)
            Files.write(filePath, content)

            val result = provider.download(key)

            assertEquals(content.size.toLong(), result.contentLength)
            assertEquals("download me", String(result.content.readAllBytes()))
        }

        @Test
        fun `throws StorageNotFoundException for non-existent key`() {
            assertThrows<StorageNotFoundException> {
                provider.download("nonexistent/file.png")
            }
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `removes file from disk`() {
            val key = "workspace1/avatar/delete-me.png"
            val filePath = tempDir.resolve(key)
            Files.createDirectories(filePath.parent)
            Files.write(filePath, "data".toByteArray())

            provider.delete(key)

            assertFalse(Files.exists(filePath), "File should be deleted")
        }

        @Test
        fun `does not throw when file is already absent`() {
            // Should be idempotent
            provider.delete("nonexistent/file.png")
        }
    }

    @Nested
    inner class Exists {

        @Test
        fun `returns true for existing file`() {
            val key = "workspace1/avatar/exists.png"
            val filePath = tempDir.resolve(key)
            Files.createDirectories(filePath.parent)
            Files.write(filePath, "data".toByteArray())

            assertTrue(provider.exists(key))
        }

        @Test
        fun `returns false for missing file`() {
            assertFalse(provider.exists("workspace1/avatar/missing.png"))
        }
    }

    @Nested
    inner class HealthCheck {

        @Test
        fun `returns true when base directory is writable`() {
            assertTrue(provider.healthCheck())
        }

        @Test
        fun `returns false when base directory does not exist`() {
            val badConfig = StorageConfigurationProperties(
                provider = "local",
                local = StorageConfigurationProperties.Local(basePath = "/nonexistent/path/that/should/not/exist")
            )
            val badProvider = LocalStorageProvider(logger, badConfig)

            assertFalse(badProvider.healthCheck())
        }
    }

    @Nested
    inner class GenerateUploadUrl {

        @Test
        fun `throws UnsupportedOperationException`() {
            assertThrows<UnsupportedOperationException> {
                provider.generateUploadUrl("workspace/avatar/file.png", "image/png", Duration.ofHours(1))
            }
        }
    }

    @Nested
    inner class PathTraversal {

        @Test
        fun `rejects keys containing dot-dot segments`() {
            val content = "malicious".toByteArray()

            assertThrows<StorageProviderException> {
                provider.upload(
                    key = "../../../etc/passwd",
                    content = ByteArrayInputStream(content),
                    contentType = "text/plain",
                    contentLength = content.size.toLong()
                )
            }
        }

        @Test
        fun `rejects download with path traversal`() {
            assertThrows<StorageProviderException> {
                provider.download("../../../etc/passwd")
            }
        }

        @Test
        fun `rejects exists check with path traversal`() {
            assertThrows<StorageProviderException> {
                provider.exists("../../etc/passwd")
            }
        }

        @Test
        fun `rejects delete with path traversal`() {
            assertThrows<StorageProviderException> {
                provider.delete("../../etc/passwd")
            }
        }
    }
}
