package riven.core.service.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.*
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
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
 * Unit tests for S3StorageProvider.
 *
 * Uses direct instantiation with a mocked S3Client to avoid Spring context
 * issues with conditional beans.
 */
class S3StorageProviderTest {

    private val logger: KLogger = mock()
    private val s3Client: S3Client = mock()

    private val config = StorageConfigurationProperties(
        provider = "s3",
        s3 = StorageConfigurationProperties.S3(
            bucket = "test-bucket",
            region = "us-east-1",
            accessKeyId = "test-key",
            secretAccessKey = "test-secret"
        )
    )

    private lateinit var provider: S3StorageProvider

    @BeforeEach
    fun setUp() {
        reset(s3Client)
        provider = S3StorageProvider(logger, s3Client, config)
    }

    // ------ Upload Tests ------

    @Nested
    inner class Upload {

        @Test
        fun `calls putObject and returns StorageResult with correct metadata`() {
            val content = "hello world".toByteArray()
            runBlocking {
                whenever(s3Client.putObject(any<PutObjectRequest>()))
                    .thenReturn(PutObjectResponse { })
            }

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
        fun `wraps SDK exceptions in StorageProviderException with cause preserved`() {
            runBlocking {
                whenever(s3Client.putObject(any<PutObjectRequest>()))
                    .thenThrow(RuntimeException("S3 error"))
            }

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
        fun `throws StorageNotFoundException for NoSuchKey errors`() {
            runBlocking {
                whenever(s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Any>()))
                    .thenThrow(NoSuchKey.invoke { })
            }

            assertThrows<StorageNotFoundException> {
                provider.download("missing/file.png")
            }
        }

        @Test
        fun `wraps non-404 SDK exceptions in StorageProviderException with cause`() {
            runBlocking {
                whenever(s3Client.getObject(any<GetObjectRequest>(), any<suspend (GetObjectResponse) -> Any>()))
                    .thenThrow(RuntimeException("Internal server error"))
            }

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
        fun `calls deleteObject without throwing`() {
            runBlocking {
                whenever(s3Client.deleteObject(any<DeleteObjectRequest>()))
                    .thenReturn(DeleteObjectResponse { })
            }

            // Should not throw -- S3 deleteObject is idempotent
            provider.delete("workspace/avatar/file.png")
        }

        @Test
        fun `wraps SDK exceptions in StorageProviderException`() {
            runBlocking {
                whenever(s3Client.deleteObject(any<DeleteObjectRequest>()))
                    .thenThrow(RuntimeException("SDK error"))
            }

            assertThrows<StorageProviderException> {
                provider.delete("workspace/avatar/file.png")
            }
        }
    }

    // ------ Exists Tests ------

    @Nested
    inner class Exists {

        @Test
        fun `returns true when headObject succeeds`() {
            runBlocking {
                whenever(s3Client.headObject(any<HeadObjectRequest>()))
                    .thenReturn(HeadObjectResponse { })
            }

            assertTrue(provider.exists("workspace/avatar/file.png"))
        }

        @Test
        fun `returns false when NoSuchKey exception`() {
            runBlocking {
                whenever(s3Client.headObject(any<HeadObjectRequest>()))
                    .thenThrow(NoSuchKey.invoke { })
            }

            assertFalse(provider.exists("workspace/avatar/file.png"))
        }

        @Test
        fun `wraps other SDK exceptions in StorageProviderException`() {
            runBlocking {
                whenever(s3Client.headObject(any<HeadObjectRequest>()))
                    .thenThrow(RuntimeException("SDK error"))
            }

            assertThrows<StorageProviderException> {
                provider.exists("workspace/avatar/file.png")
            }
        }
    }

    // ------ Generate Upload URL Tests ------

    @Nested
    inner class GenerateUploadUrl {

        @Test
        fun `wraps errors in StorageProviderException`() {
            // presignPutObject is an extension function on S3Client, making direct mocking
            // complex. A non-mocked S3Client will fail during presigning.
            // We verify the exception wrapping behavior.
            val ex = assertThrows<StorageProviderException> {
                provider.generateUploadUrl("workspace/avatar/file.png", "image/png", Duration.ofHours(1))
            }

            assertEquals("Upload URL generation failed for key: workspace/avatar/file.png", ex.message)
        }
    }

    // ------ Generate Signed URL Tests ------

    @Nested
    inner class GenerateSignedUrl {

        @Test
        fun `wraps errors in StorageProviderException`() {
            // presignGetObject is an extension function on S3Client, making direct mocking
            // complex. A non-mocked S3Client will fail during presigning.
            // We verify the exception wrapping behavior.
            val ex = assertThrows<StorageProviderException> {
                provider.generateSignedUrl("workspace/avatar/file.png", Duration.ofHours(1))
            }

            assertEquals("Signed URL generation failed for key: workspace/avatar/file.png", ex.message)
        }
    }

    // ------ Health Check Tests ------

    @Nested
    inner class HealthCheck {

        @Test
        fun `returns true when headBucket succeeds`() {
            runBlocking {
                whenever(s3Client.headBucket(any<HeadBucketRequest>()))
                    .thenReturn(HeadBucketResponse { })
            }

            assertTrue(provider.healthCheck())
        }

        @Test
        fun `auto-creates bucket when headBucket fails and returns true`() {
            runBlocking {
                whenever(s3Client.headBucket(any<HeadBucketRequest>()))
                    .thenThrow(NotFound.invoke { })
                whenever(s3Client.createBucket(any<CreateBucketRequest>()))
                    .thenReturn(CreateBucketResponse { })
            }

            assertTrue(provider.healthCheck())
        }

        @Test
        fun `returns false when both headBucket and createBucket fail`() {
            runBlocking {
                whenever(s3Client.headBucket(any<HeadBucketRequest>()))
                    .thenThrow(RuntimeException("Connection refused"))
                whenever(s3Client.createBucket(any<CreateBucketRequest>()))
                    .thenThrow(RuntimeException("Still failing"))
            }

            assertFalse(provider.healthCheck())
        }
    }
}
