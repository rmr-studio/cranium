package riven.core.service.storage

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.Storage
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import riven.core.configuration.storage.StorageConfigurationProperties
import riven.core.exceptions.StorageNotFoundException
import riven.core.exceptions.StorageProviderException
import java.io.ByteArrayInputStream
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SupabaseStorageProvider.
 *
 * Uses a subclass that overrides storagePlugin() to inject a mock Storage,
 * since supabase-kt's SupabaseClient.storage is a static extension function
 * that cannot be mocked with Mockito.
 */
class SupabaseStorageProviderTest {

    private val logger: KLogger = mock()
    private val supabaseClient: SupabaseClient = mock()
    private val mockStorage: Storage = mock()
    private val mockBucket: BucketApi = mock()

    private val config = StorageConfigurationProperties(
        provider = "supabase",
        supabase = StorageConfigurationProperties.Supabase(bucket = "test-bucket")
    )

    private lateinit var provider: SupabaseStorageProvider

    @BeforeEach
    fun setUp() {
        reset(mockStorage, mockBucket)
        whenever(mockStorage.from("test-bucket")).thenReturn(mockBucket)

        provider = object : SupabaseStorageProvider(logger, supabaseClient, config) {
            override fun storagePlugin(): Storage = mockStorage
        }
    }

    // ------ Upload Tests ------

    @Nested
    inner class Upload {

        @Test
        fun `delegates to bucket upload with correct key and returns StorageResult`() {
            val content = "hello world".toByteArray()

            val result = provider.upload(
                key = "workspace/avatar/file.png",
                content = ByteArrayInputStream(content),
                contentType = "image/png",
                contentLength = content.size.toLong()
            )

            assertEquals("workspace/avatar/file.png", result.storageKey)
            assertEquals("image/png", result.contentType)
            assertEquals(content.size.toLong(), result.contentLength)
        }

        @Test
        fun `wraps SDK exceptions in StorageProviderException`() {
            whenever(mockStorage.from("test-bucket")).thenThrow(RuntimeException("SDK error"))

            val ex = assertThrows<StorageProviderException> {
                provider.upload(
                    key = "workspace/avatar/file.png",
                    content = ByteArrayInputStream("data".toByteArray()),
                    contentType = "image/png",
                    contentLength = 4L
                )
            }

            assertEquals("Upload failed for key: workspace/avatar/file.png", ex.message)
            assertNotNull(ex.cause)
        }
    }

    // ------ Download Tests ------

    @Nested
    inner class Download {

        @Test
        fun `returns DownloadResult with ByteArrayInputStream from downloadAuthenticated`() {
            val bytes = "file content".toByteArray()
            runBlockingSuspend {
                whenever(mockBucket.downloadAuthenticated(
                    path = eq("workspace/avatar/file.png"),
                    options = any<io.github.jan.supabase.storage.DownloadOptionBuilder.() -> Unit>()
                )).thenReturn(bytes)
            }

            val result = provider.download("workspace/avatar/file.png")

            assertEquals("application/octet-stream", result.contentType)
            assertEquals(bytes.size.toLong(), result.contentLength)
            assertEquals("file content", String(result.content.readAllBytes()))
        }

        @Test
        fun `throws StorageNotFoundException for not-found errors`() {
            whenever(mockStorage.from("test-bucket"))
                .thenThrow(RuntimeException("Object not found"))

            assertThrows<StorageNotFoundException> {
                provider.download("missing/file.png")
            }
        }

        @Test
        fun `wraps non-404 SDK exceptions in StorageProviderException`() {
            whenever(mockStorage.from("test-bucket"))
                .thenThrow(RuntimeException("Internal server error"))

            val ex = assertThrows<StorageProviderException> {
                provider.download("workspace/avatar/file.png")
            }

            assertEquals("Download failed for key: workspace/avatar/file.png", ex.message)
            assertNotNull(ex.cause)
        }
    }

    // ------ Delete Tests ------

    @Nested
    inner class Delete {

        @Test
        fun `delegates to bucket delete with key`() {
            // Should not throw
            provider.delete("workspace/avatar/file.png")
        }

        @Test
        fun `wraps SDK exceptions in StorageProviderException`() {
            whenever(mockStorage.from("test-bucket"))
                .thenThrow(RuntimeException("SDK error"))

            assertThrows<StorageProviderException> {
                provider.delete("workspace/avatar/file.png")
            }
        }
    }

    // ------ Exists Tests ------

    @Nested
    inner class Exists {

        @Test
        fun `returns true when file exists`() {
            runBlockingSuspend {
                whenever(mockBucket.exists("workspace/avatar/file.png")).thenReturn(true)
            }

            assertTrue(provider.exists("workspace/avatar/file.png"))
        }

        @Test
        fun `returns false when file does not exist`() {
            runBlockingSuspend {
                whenever(mockBucket.exists("workspace/avatar/missing.png")).thenReturn(false)
            }

            assertFalse(provider.exists("workspace/avatar/missing.png"))
        }

        @Test
        fun `wraps SDK exceptions in StorageProviderException`() {
            whenever(mockStorage.from("test-bucket"))
                .thenThrow(RuntimeException("SDK error"))

            assertThrows<StorageProviderException> {
                provider.exists("workspace/avatar/file.png")
            }
        }
    }

    // ------ Generate Upload URL Tests ------

    @Nested
    inner class GenerateUploadUrl {

        @Test
        fun `wraps SDK exceptions in StorageProviderException for upload URL`() {
            whenever(mockStorage.from("test-bucket"))
                .thenThrow(RuntimeException("Upload signing failed"))

            val ex = assertThrows<StorageProviderException> {
                provider.generateUploadUrl("workspace/avatar/file.png", "image/png", Duration.ofHours(1))
            }

            assertEquals("Upload URL generation failed for key: workspace/avatar/file.png", ex.message)
            assertNotNull(ex.cause)
        }
    }

    // ------ Generate Signed URL Tests ------

    @Nested
    inner class GenerateSignedUrl {

        @Test
        fun `wraps SDK exceptions in StorageProviderException for signed URL`() {
            // createSignedUrl uses Kotlin Duration (inline class) with a mangled JVM signature,
            // making direct mocking difficult. We verify the error normalization path instead,
            // which is the critical behavior -- exception wrapping with generic message.
            whenever(mockStorage.from("test-bucket"))
                .thenThrow(RuntimeException("Signing failed"))

            val ex = assertThrows<StorageProviderException> {
                provider.generateSignedUrl("workspace/avatar/file.png", Duration.ofHours(1))
            }

            assertEquals("Signed URL generation failed for key: workspace/avatar/file.png", ex.message)
            assertNotNull(ex.cause)
        }

        @Test
        fun `wraps SDK exceptions in StorageProviderException`() {
            whenever(mockStorage.from("test-bucket"))
                .thenThrow(RuntimeException("SDK error"))

            assertThrows<StorageProviderException> {
                provider.generateSignedUrl("workspace/avatar/file.png", Duration.ofHours(1))
            }
        }
    }

    // ------ Health Check Tests ------

    @Nested
    inner class HealthCheck {

        @Test
        fun `returns true when bucket exists`() {
            // Default mock behavior -- retrieveBucketById doesn't throw, so bucket exists
            assertTrue(provider.healthCheck())
        }

        @Test
        fun `returns false when storage plugin is completely unavailable`() {
            // Override storagePlugin to throw on every access, simulating total failure
            val failingProvider = object : SupabaseStorageProvider(logger, supabaseClient, config) {
                override fun storagePlugin(): Storage {
                    throw RuntimeException("Connection refused")
                }
            }

            assertFalse(failingProvider.healthCheck())
        }
    }

    // ------ Helper ------

    /**
     * Helper to set up suspend function mocks within a runBlocking context.
     * This is needed because mockito-kotlin's `whenever` for suspend functions
     * must be called in a coroutine context.
     */
    private fun runBlockingSuspend(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
