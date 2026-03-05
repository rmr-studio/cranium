package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.multipart.MultipartFile
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.entity.storage.FileMetadataEntity
import riven.core.enums.storage.StorageDomain
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ContentTypeNotAllowedException
import riven.core.exceptions.FileSizeLimitExceededException
import riven.core.exceptions.NotFoundException
import riven.core.exceptions.SignedUrlExpiredException
import riven.core.models.storage.DownloadResult
import riven.core.models.storage.StorageProvider
import riven.core.models.storage.StorageResult
import riven.core.repository.storage.FileMetadataRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import riven.core.service.util.factory.storage.StorageFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Duration
import java.util.*

@SpringBootTest(
    classes = [
        AuthTokenService::class,
        WorkspaceSecurity::class,
        StorageServiceTest.TestConfig::class,
        StorageService::class
    ]
)
@WithUserPersona(
    userId = "f8b1c2d3-4e5f-6789-abcd-ef0123456789",
    email = "test@test.com",
    displayName = "Test User",
    roles = [
        WorkspaceRole(
            workspaceId = "f8b1c2d3-4e5f-6789-abcd-ef9876543210",
            role = WorkspaceRoles.OWNER
        )
    ]
)
class StorageServiceTest {

    @Configuration
    class TestConfig

    private val userId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210")

    @MockitoBean
    private lateinit var logger: KLogger

    @MockitoBean
    private lateinit var storageProvider: StorageProvider

    @MockitoBean
    private lateinit var contentValidationService: ContentValidationService

    @MockitoBean
    private lateinit var signedUrlService: SignedUrlService

    @MockitoBean
    private lateinit var fileMetadataRepository: FileMetadataRepository

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var authTokenService: AuthTokenService

    @Autowired
    private lateinit var storageService: StorageService

    private val testStorageKey = "$workspaceId/avatar/${UUID.randomUUID()}.png"

    @BeforeEach
    fun setUp() {
        reset(
            storageProvider, contentValidationService, signedUrlService,
            fileMetadataRepository, activityService, authTokenService
        )
        whenever(authTokenService.getUserId()).thenReturn(userId)
    }

    // ------ Upload Tests ------

    @Test
    fun `uploadFile detects type, validates, stores, persists metadata, logs activity, returns signed URL`() {
        val file = mockMultipartFile("test-image.png", "image/png", ByteArray(1024))
        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = testStorageKey,
            originalFilename = "test-image.png",
            contentType = "image/png",
            fileSize = 1024L,
            uploadedBy = userId
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("test-image.png")))
            .thenReturn("image/png")
        whenever(contentValidationService.generateStorageKey(eq(workspaceId), eq(StorageDomain.AVATAR), eq("image/png")))
            .thenReturn(testStorageKey)
        whenever(storageProvider.upload(eq(testStorageKey), any(), eq("image/png"), eq(1024L)))
            .thenReturn(StorageResult(testStorageKey, "image/png", 1024L))
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>()))
            .thenReturn(savedEntity)
        whenever(signedUrlService.generateDownloadUrl(eq(testStorageKey), any()))
            .thenReturn("/api/v1/storage/download/some-token")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.uploadFile(workspaceId, StorageDomain.AVATAR, file)

        assertNotNull(result)
        assertEquals("test-image.png", result.file.originalFilename)
        assertEquals("/api/v1/storage/download/some-token", result.signedUrl)

        // Verify validation happened before storage
        val inOrder = inOrder(contentValidationService, storageProvider, fileMetadataRepository, activityService)
        inOrder.verify(contentValidationService).detectContentType(any<InputStream>(), eq("test-image.png"))
        inOrder.verify(contentValidationService).validateContentType(eq(StorageDomain.AVATAR), eq("image/png"))
        inOrder.verify(contentValidationService).validateFileSize(eq(StorageDomain.AVATAR), eq(1024L))
        inOrder.verify(storageProvider).upload(eq(testStorageKey), any(), eq("image/png"), eq(1024L))
        inOrder.verify(fileMetadataRepository).save(any<FileMetadataEntity>())

        // Verify activity was logged
        verify(activityService).logActivity(any(), any(), eq(userId), eq(workspaceId), any(), any(), any(), any())
    }

    @Test
    fun `uploadFile rejects disallowed content type before storing`() {
        val file = mockMultipartFile("test.exe", "application/exe", ByteArray(512))

        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("test.exe")))
            .thenReturn("application/x-msdownload")
        whenever(contentValidationService.validateContentType(eq(StorageDomain.AVATAR), eq("application/x-msdownload")))
            .thenThrow(ContentTypeNotAllowedException("Content type not allowed"))

        assertThrows<ContentTypeNotAllowedException> {
            storageService.uploadFile(workspaceId, StorageDomain.AVATAR, file)
        }

        verify(storageProvider, never()).upload(any(), any(), any(), any())
        verify(fileMetadataRepository, never()).save(any<FileMetadataEntity>())
    }

    @Test
    fun `uploadFile rejects oversized file before storing`() {
        val largeBytes = ByteArray(10_000_000) // 10MB
        val file = mockMultipartFile("large.png", "image/png", largeBytes)

        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("large.png")))
            .thenReturn("image/png")
        whenever(contentValidationService.validateFileSize(eq(StorageDomain.AVATAR), eq(10_000_000L)))
            .thenThrow(FileSizeLimitExceededException("File too large"))

        assertThrows<FileSizeLimitExceededException> {
            storageService.uploadFile(workspaceId, StorageDomain.AVATAR, file)
        }

        verify(storageProvider, never()).upload(any(), any(), any(), any())
        verify(fileMetadataRepository, never()).save(any<FileMetadataEntity>())
    }

    @Test
    fun `uploadFile sanitizes SVG content before storage`() {
        val svgBytes = "<svg><script>alert('xss')</script></svg>".toByteArray()
        val sanitizedBytes = "<svg></svg>".toByteArray()
        val file = mockMultipartFile("icon.svg", "image/svg+xml", svgBytes)
        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = testStorageKey,
            contentType = "image/svg+xml",
            fileSize = sanitizedBytes.size.toLong(),
            uploadedBy = userId
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("icon.svg")))
            .thenReturn("image/svg+xml")
        whenever(contentValidationService.sanitizeSvg(any()))
            .thenReturn(sanitizedBytes)
        whenever(contentValidationService.generateStorageKey(eq(workspaceId), eq(StorageDomain.AVATAR), eq("image/svg+xml")))
            .thenReturn(testStorageKey)
        whenever(storageProvider.upload(eq(testStorageKey), any(), eq("image/svg+xml"), eq(sanitizedBytes.size.toLong())))
            .thenReturn(StorageResult(testStorageKey, "image/svg+xml", sanitizedBytes.size.toLong()))
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>()))
            .thenReturn(savedEntity)
        whenever(signedUrlService.generateDownloadUrl(eq(testStorageKey), any()))
            .thenReturn("/api/v1/storage/download/svg-token")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.uploadFile(workspaceId, StorageDomain.AVATAR, file)

        assertNotNull(result)
        verify(contentValidationService).sanitizeSvg(any())
        // Verify that sanitized content size was used for upload
        verify(storageProvider).upload(eq(testStorageKey), any(), eq("image/svg+xml"), eq(sanitizedBytes.size.toLong()))
    }

    // ------ Get File Tests ------

    @Test
    fun `getFile returns metadata for existing file`() {
        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))

        val result = storageService.getFile(workspaceId, fileId)

        assertEquals(fileId, result.id)
        assertEquals(workspaceId, result.workspaceId)
    }

    @Test
    fun `getFile throws NotFoundException for non-existent file`() {
        val fileId = UUID.randomUUID()

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.empty())

        assertThrows<NotFoundException> {
            storageService.getFile(workspaceId, fileId)
        }
    }

    // ------ Generate Signed URL Tests ------

    @Test
    fun `generateSignedUrl returns signed URL for existing file`() {
        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId,
            storageKey = testStorageKey
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))
        whenever(signedUrlService.generateDownloadUrl(eq(testStorageKey), any()))
            .thenReturn("/api/v1/storage/download/url-token")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))

        val result = storageService.generateSignedUrl(workspaceId, fileId, null)

        assertNotNull(result)
        assertEquals("/api/v1/storage/download/url-token", result.url)
        assertNotNull(result.expiresAt)
    }

    @Test
    fun `generateSignedUrl accepts custom expiry duration`() {
        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId,
            storageKey = testStorageKey
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))
        whenever(signedUrlService.generateDownloadUrl(eq(testStorageKey), eq(Duration.ofSeconds(1800))))
            .thenReturn("/api/v1/storage/download/custom-token")

        val result = storageService.generateSignedUrl(workspaceId, fileId, 1800L)

        assertNotNull(result)
        verify(signedUrlService).generateDownloadUrl(eq(testStorageKey), eq(Duration.ofSeconds(1800)))
    }

    // ------ Delete File Tests ------

    @Test
    fun `deleteFile soft-deletes metadata then removes physical file and logs activity`() {
        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId,
            storageKey = testStorageKey
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>()))
            .thenReturn(entity)
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        storageService.deleteFile(workspaceId, fileId)

        // Verify soft-delete was saved before physical delete
        val inOrder = inOrder(fileMetadataRepository, storageProvider, activityService)
        inOrder.verify(fileMetadataRepository).save(argThat<FileMetadataEntity> {
            this.deleted && this.deletedAt != null
        })
        inOrder.verify(storageProvider).delete(testStorageKey)

        // Verify activity was logged
        verify(activityService).logActivity(any(), any(), eq(userId), eq(workspaceId), any(), any(), any(), any())
    }

    @Test
    fun `deleteFile continues even if physical file deletion fails`() {
        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId,
            storageKey = testStorageKey
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>()))
            .thenReturn(entity)
        whenever(storageProvider.delete(testStorageKey))
            .thenThrow(RuntimeException("Provider unavailable"))
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        // Should not throw despite provider failure
        assertDoesNotThrow {
            storageService.deleteFile(workspaceId, fileId)
        }

        // Activity still logged
        verify(activityService).logActivity(any(), any(), eq(userId), eq(workspaceId), any(), any(), any(), any())
    }

    // ------ List Files Tests ------

    @Test
    fun `listFiles returns files filtered by workspace`() {
        val entities = listOf(
            StorageFactory.fileMetadataEntity(workspaceId = workspaceId).apply {
                createdAt = java.time.ZonedDateTime.now(); updatedAt = java.time.ZonedDateTime.now()
            },
            StorageFactory.fileMetadataEntity(workspaceId = workspaceId).apply {
                createdAt = java.time.ZonedDateTime.now(); updatedAt = java.time.ZonedDateTime.now()
            }
        )

        whenever(fileMetadataRepository.findByWorkspaceId(workspaceId))
            .thenReturn(entities)

        val result = storageService.listFiles(workspaceId, null)

        assertEquals(2, result.files.size)
        verify(fileMetadataRepository).findByWorkspaceId(workspaceId)
        verify(fileMetadataRepository, never()).findByWorkspaceIdAndDomain(any(), any())
    }

    @Test
    fun `listFiles returns files filtered by workspace and domain`() {
        val entities = listOf(
            StorageFactory.fileMetadataEntity(workspaceId = workspaceId, domain = StorageDomain.AVATAR).apply {
                createdAt = java.time.ZonedDateTime.now(); updatedAt = java.time.ZonedDateTime.now()
            }
        )

        whenever(fileMetadataRepository.findByWorkspaceIdAndDomain(workspaceId, StorageDomain.AVATAR))
            .thenReturn(entities)

        val result = storageService.listFiles(workspaceId, StorageDomain.AVATAR)

        assertEquals(1, result.files.size)
        verify(fileMetadataRepository).findByWorkspaceIdAndDomain(workspaceId, StorageDomain.AVATAR)
        verify(fileMetadataRepository, never()).findByWorkspaceId(any())
    }

    // ------ Download File Tests ------

    @Test
    fun `downloadFile validates token and returns file with original filename`() {
        val token = "valid-token"
        val content = ByteArrayInputStream("file-content".toByteArray())
        val providerResult = DownloadResult(
            content = content,
            contentType = "image/png",
            contentLength = 12L,
            originalFilename = null
        )
        val entity = StorageFactory.fileMetadataEntity(
            storageKey = testStorageKey,
            originalFilename = "my-photo.png"
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(signedUrlService.validateToken(token))
            .thenReturn(Pair(testStorageKey, System.currentTimeMillis() / 1000 + 3600))
        whenever(storageProvider.download(testStorageKey))
            .thenReturn(providerResult)
        whenever(fileMetadataRepository.findByStorageKey(testStorageKey))
            .thenReturn(Optional.of(entity))

        val result = storageService.downloadFile(token)

        assertNotNull(result)
        assertEquals("image/png", result.contentType)
        assertEquals("my-photo.png", result.originalFilename)
    }

    @Test
    fun `downloadFile throws SignedUrlExpiredException for invalid token`() {
        val token = "invalid-token"

        whenever(signedUrlService.validateToken(token))
            .thenReturn(null)

        assertThrows<SignedUrlExpiredException> {
            storageService.downloadFile(token)
        }

        verify(storageProvider, never()).download(any())
    }

    // ------ Helper ------

    private fun mockMultipartFile(
        filename: String,
        contentType: String,
        content: ByteArray
    ): MultipartFile = mock {
        on { originalFilename } doReturn filename
        on { this.contentType } doReturn contentType
        on { bytes } doReturn content
        on { size } doReturn content.size.toLong()
    }
}
