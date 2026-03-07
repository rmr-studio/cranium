package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.multipart.MultipartFile
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.configuration.storage.StorageConfigurationProperties
import riven.core.entity.storage.FileMetadataEntity
import riven.core.enums.storage.StorageDomain
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.exceptions.ContentTypeNotAllowedException
import riven.core.exceptions.FileSizeLimitExceededException
import riven.core.exceptions.NotFoundException
import riven.core.exceptions.SignedUrlExpiredException
import riven.core.exceptions.StorageNotFoundException
import riven.core.models.request.storage.ConfirmUploadRequest
import riven.core.models.request.storage.BatchDeleteRequest
import riven.core.models.request.storage.UpdateMetadataRequest
import riven.core.models.storage.DownloadResult
import riven.core.models.storage.StorageProvider
import riven.core.models.storage.StorageResult
import riven.core.repository.storage.FileMetadataRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.util.SecurityTestConfig
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
        SecurityTestConfig::class,
        StorageService::class,
        StorageConfigurationProperties::class
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
    private lateinit var storageConfig: StorageConfigurationProperties

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
            storageConfig, fileMetadataRepository, activityService, authTokenService
        )
        whenever(authTokenService.getUserId()).thenReturn(userId)
        // Default: simulate local adapter behavior where generateSignedUrl is unsupported,
        // causing StorageService to fall back to SignedUrlService.
        whenever(storageProvider.generateSignedUrl(any(), any()))
            .thenThrow(UnsupportedOperationException("Use SignedUrlService for local signed URLs"))
        // Default presigned upload config
        whenever(storageConfig.presignedUpload)
            .thenReturn(StorageConfigurationProperties.PresignedUpload(expirySeconds = 900))
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

    // ------ Signed URL Fallback Tests ------

    @Test
    fun `generateSignedUrl uses provider signed URL when provider supports it`() {
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
        whenever(storageProvider.generateSignedUrl(eq(testStorageKey), any()))
            .thenReturn("https://supabase.co/storage/v1/sign/bucket/file")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))

        val result = storageService.generateSignedUrl(workspaceId, fileId, null)

        assertEquals("https://supabase.co/storage/v1/sign/bucket/file", result.url)
        verify(signedUrlService, never()).generateDownloadUrl(any(), any())
    }

    @Test
    fun `generateSignedUrl falls back to signedUrlService when provider throws UnsupportedOperationException`() {
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
        whenever(storageProvider.generateSignedUrl(eq(testStorageKey), any()))
            .thenThrow(UnsupportedOperationException("Use SignedUrlService for local signed URLs"))
        whenever(signedUrlService.generateDownloadUrl(eq(testStorageKey), any()))
            .thenReturn("/api/v1/storage/download/fallback-token")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))

        val result = storageService.generateSignedUrl(workspaceId, fileId, null)

        assertEquals("/api/v1/storage/download/fallback-token", result.url)
        verify(signedUrlService).generateDownloadUrl(eq(testStorageKey), any())
    }

    @Test
    fun `uploadFile uses provider signed URL for response when provider supports it`() {
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
        whenever(storageProvider.generateSignedUrl(eq(testStorageKey), any()))
            .thenReturn("https://supabase.co/storage/v1/sign/bucket/file")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.uploadFile(workspaceId, StorageDomain.AVATAR, file)

        assertEquals("https://supabase.co/storage/v1/sign/bucket/file", result.signedUrl)
        verify(signedUrlService, never()).generateDownloadUrl(any(), any())
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

    // ------ Presigned Upload Tests ------

    @Test
    fun `requestPresignedUpload returns presigned URL when provider supports it`() {
        whenever(contentValidationService.generateStorageKey(eq(workspaceId), eq(StorageDomain.AVATAR), eq("image/png")))
            .thenReturn(testStorageKey)
        whenever(storageProvider.generateUploadUrl(eq(testStorageKey), eq("image/png"), eq(Duration.ofSeconds(900))))
            .thenReturn("https://s3.amazonaws.com/bucket/presigned-upload-url")

        val result = storageService.requestPresignedUpload(workspaceId, StorageDomain.AVATAR, "image/png")

        assertTrue(result.supported)
        assertEquals(testStorageKey, result.storageKey)
        assertEquals("https://s3.amazonaws.com/bucket/presigned-upload-url", result.uploadUrl)
        assertEquals("PUT", result.method)
    }

    @Test
    fun `requestPresignedUpload returns unsupported when provider throws UnsupportedOperationException`() {
        whenever(contentValidationService.generateStorageKey(eq(workspaceId), eq(StorageDomain.AVATAR), eq("image/png")))
            .thenReturn(testStorageKey)
        whenever(storageProvider.generateUploadUrl(eq(testStorageKey), eq("image/png"), eq(Duration.ofSeconds(900))))
            .thenThrow(UnsupportedOperationException("Local provider does not support presigned uploads"))

        val result = storageService.requestPresignedUpload(workspaceId, StorageDomain.AVATAR, "image/png")

        assertFalse(result.supported)
        assertEquals(testStorageKey, result.storageKey)
        assertNull(result.uploadUrl)
        assertNull(result.method)
    }

    @Test
    fun `confirmPresignedUpload validates file and persists metadata`() {
        val storageKey = "$workspaceId/avatar/${UUID.randomUUID()}.png"
        val request = ConfirmUploadRequest(storageKey = storageKey, originalFilename = "photo.png")
        val fileBytes = ByteArray(2048)
        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = storageKey,
            originalFilename = "photo.png",
            contentType = "image/png",
            fileSize = 2048L,
            uploadedBy = userId
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(storageProvider.exists(storageKey)).thenReturn(true)
        whenever(storageProvider.download(storageKey)).thenReturn(
            DownloadResult(ByteArrayInputStream(fileBytes), "application/octet-stream", fileBytes.size.toLong(), null)
        )
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("photo.png")))
            .thenReturn("image/png")
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>()))
            .thenReturn(savedEntity)
        whenever(signedUrlService.generateDownloadUrl(eq(storageKey), any()))
            .thenReturn("/api/v1/storage/download/some-token")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.confirmPresignedUpload(workspaceId, request)

        assertNotNull(result)
        assertEquals("photo.png", result.file.originalFilename)
        verify(storageProvider).exists(storageKey)
        verify(storageProvider).download(storageKey)
        verify(contentValidationService).detectContentType(any<InputStream>(), eq("photo.png"))
        verify(contentValidationService).validateContentType(StorageDomain.AVATAR, "image/png")
        verify(fileMetadataRepository).save(any<FileMetadataEntity>())
    }

    @Test
    fun `confirmPresignedUpload deletes file and throws when content type validation fails`() {
        val storageKey = "$workspaceId/avatar/${UUID.randomUUID()}.png"
        val request = ConfirmUploadRequest(storageKey = storageKey, originalFilename = "malware.exe")
        val fileBytes = ByteArray(512)

        whenever(storageProvider.exists(storageKey)).thenReturn(true)
        whenever(storageProvider.download(storageKey)).thenReturn(
            DownloadResult(ByteArrayInputStream(fileBytes), "application/octet-stream", fileBytes.size.toLong(), null)
        )
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("malware.exe")))
            .thenReturn("application/x-msdownload")
        whenever(contentValidationService.validateContentType(StorageDomain.AVATAR, "application/x-msdownload"))
            .thenThrow(ContentTypeNotAllowedException("Content type not allowed"))

        assertThrows<ContentTypeNotAllowedException> {
            storageService.confirmPresignedUpload(workspaceId, request)
        }

        verify(storageProvider).delete(storageKey)
        verify(fileMetadataRepository, never()).save(any<FileMetadataEntity>())
    }

    @Test
    fun `confirmPresignedUpload throws StorageNotFoundException when file does not exist`() {
        val storageKey = "$workspaceId/avatar/${UUID.randomUUID()}.png"
        val request = ConfirmUploadRequest(storageKey = storageKey, originalFilename = "missing.png")

        whenever(storageProvider.exists(storageKey)).thenReturn(false)

        assertThrows<StorageNotFoundException> {
            storageService.confirmPresignedUpload(workspaceId, request)
        }

        verify(storageProvider, never()).download(any())
    }

    @Test
    fun `confirmPresignedUpload persists optional metadata`() {
        val storageKey = "$workspaceId/avatar/${UUID.randomUUID()}.png"
        val customMetadata = mapOf("source" to "camera", "description" to "profile photo")
        val request = ConfirmUploadRequest(storageKey = storageKey, originalFilename = "photo.png", metadata = customMetadata)
        val fileBytes = ByteArray(2048)
        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = storageKey,
            originalFilename = "photo.png",
            contentType = "image/png",
            fileSize = 2048L,
            uploadedBy = userId,
            metadata = customMetadata
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(storageProvider.exists(storageKey)).thenReturn(true)
        whenever(storageProvider.download(storageKey)).thenReturn(
            DownloadResult(ByteArrayInputStream(fileBytes), "application/octet-stream", fileBytes.size.toLong(), null)
        )
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("photo.png")))
            .thenReturn("image/png")
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>()))
            .thenReturn(savedEntity)
        whenever(signedUrlService.generateDownloadUrl(eq(storageKey), any()))
            .thenReturn("/api/v1/storage/download/some-token")
        whenever(signedUrlService.getDefaultExpiry()).thenReturn(Duration.ofHours(1))
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.confirmPresignedUpload(workspaceId, request)

        assertNotNull(result)
        verify(fileMetadataRepository).save(argThat<FileMetadataEntity> {
            this.metadata == customMetadata
        })
    }

    // ------ Metadata Tests ------

    @Test
    fun `updateMetadata merges new keys, preserves existing, removes null-value keys`() {
        val fileId = UUID.randomUUID()
        val existingMetadata = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId,
            metadata = existingMetadata
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

        val request = UpdateMetadataRequest(
            metadata = mapOf("key2" to "updated", "key3" to null, "key4" to "new")
        )

        val result = storageService.updateMetadata(workspaceId, fileId, request)

        verify(fileMetadataRepository).save(argThat<FileMetadataEntity> {
            val meta = this.metadata!!
            meta["key1"] == "value1" && meta["key2"] == "updated" && !meta.containsKey("key3") && meta["key4"] == "new"
        })
    }

    @Test
    fun `updateMetadata creates new metadata map when entity has no existing metadata`() {
        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId,
            metadata = null
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

        val request = UpdateMetadataRequest(metadata = mapOf("newKey" to "newValue"))

        storageService.updateMetadata(workspaceId, fileId, request)

        verify(fileMetadataRepository).save(argThat<FileMetadataEntity> {
            this.metadata == mapOf("newKey" to "newValue")
        })
    }

    @Test
    fun `updateMetadata logs FILE_UPDATE activity with old and new metadata`() {
        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(
            id = fileId,
            workspaceId = workspaceId,
            metadata = mapOf("existing" to "value")
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

        val request = UpdateMetadataRequest(metadata = mapOf("new" to "data"))

        storageService.updateMetadata(workspaceId, fileId, request)

        verify(activityService).logActivity(any(), any(), eq(userId), eq(workspaceId), any(), eq(fileId), any(), any())
    }

    @Test
    fun `validateMetadata rejects more than 20 key-value pairs`() {
        val tooManyPairs = (1..21).associate { "key$it" to "value$it" }
        val request = UpdateMetadataRequest(metadata = tooManyPairs.mapValues { it.value as String? })

        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(id = fileId, workspaceId = workspaceId).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))

        assertThrows<IllegalArgumentException> {
            storageService.updateMetadata(workspaceId, fileId, request)
        }
    }

    @Test
    fun `validateMetadata rejects invalid key pattern`() {
        val request = UpdateMetadataRequest(metadata = mapOf("invalid key!" to "value"))

        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(id = fileId, workspaceId = workspaceId).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))

        assertThrows<IllegalArgumentException> {
            storageService.updateMetadata(workspaceId, fileId, request)
        }
    }

    @Test
    fun `validateMetadata rejects values longer than 1024 characters`() {
        val longValue = "x".repeat(1025)
        val request = UpdateMetadataRequest(metadata = mapOf("key" to longValue))

        val fileId = UUID.randomUUID()
        val entity = StorageFactory.fileMetadataEntity(id = fileId, workspaceId = workspaceId).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
            .thenReturn(Optional.of(entity))

        assertThrows<IllegalArgumentException> {
            storageService.updateMetadata(workspaceId, fileId, request)
        }
    }

    @Test
    fun `uploadFile with optional metadata persists metadata alongside file`() {
        val customMetadata = mapOf("source" to "upload", "type" to "profile")
        val file = mockMultipartFile("test-image.png", "image/png", ByteArray(1024))
        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = testStorageKey,
            originalFilename = "test-image.png",
            contentType = "image/png",
            fileSize = 1024L,
            uploadedBy = userId,
            metadata = customMetadata
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

        val result = storageService.uploadFile(workspaceId, StorageDomain.AVATAR, file, customMetadata)

        assertNotNull(result)
        verify(fileMetadataRepository).save(argThat<FileMetadataEntity> {
            this.metadata == customMetadata
        })
    }

    // ------ Batch Upload Tests ------

    @Test
    fun `batchUpload with 3 valid files returns BatchUploadResponse with 3 success results`() {
        val files = (1..3).map { i ->
            val file = mockMultipartFile("file$i.png", "image/png", ByteArray(1024))
            file
        }

        setupUploadMocks()

        val result = storageService.batchUpload(workspaceId, StorageDomain.AVATAR, files)

        assertEquals(3, result.succeeded)
        assertEquals(0, result.failed)
        assertEquals(3, result.results.size)
        result.results.forEach { item ->
            assertEquals(201, item.status)
            assertNull(item.error)
            assertNotNull(item.id)
        }
    }

    @Test
    fun `batchUpload with 1 valid and 1 invalid returns mixed results`() {
        val validFile = mockMultipartFile("valid.png", "image/png", ByteArray(1024))
        val invalidFile = mockMultipartFile("invalid.exe", "application/exe", ByteArray(512))

        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = testStorageKey,
            originalFilename = "valid.png",
            contentType = "image/png",
            fileSize = 1024L,
            uploadedBy = userId
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        // First file succeeds
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("valid.png")))
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

        // Second file fails on content type
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("invalid.exe")))
            .thenReturn("application/x-msdownload")
        whenever(contentValidationService.validateContentType(eq(StorageDomain.AVATAR), eq("application/x-msdownload")))
            .thenThrow(ContentTypeNotAllowedException("Content type not allowed"))

        val result = storageService.batchUpload(workspaceId, StorageDomain.AVATAR, listOf(validFile, invalidFile))

        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(201, result.results[0].status)
        assertNull(result.results[0].error)
        assertEquals(415, result.results[1].status)
        assertNotNull(result.results[1].error)
    }

    @Test
    fun `batchUpload with more than 10 files throws IllegalArgumentException`() {
        val files = (1..11).map { mockMultipartFile("file$it.png", "image/png", ByteArray(100)) }

        assertThrows<IllegalArgumentException> {
            storageService.batchUpload(workspaceId, StorageDomain.AVATAR, files)
        }
    }

    @Test
    fun `batchUpload with empty list throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            storageService.batchUpload(workspaceId, StorageDomain.AVATAR, emptyList())
        }
    }

    @Test
    fun `batchUpload individual item failure does not prevent other items from being processed`() {
        val file1 = mockMultipartFile("file1.png", "image/png", ByteArray(1024))
        val file2 = mockMultipartFile("file2.exe", "application/exe", ByteArray(512))
        val file3 = mockMultipartFile("file3.png", "image/png", ByteArray(1024))

        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = testStorageKey,
            originalFilename = "file1.png",
            contentType = "image/png",
            fileSize = 1024L,
            uploadedBy = userId
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        // file1 and file3 succeed (both .png)
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("file1.png")))
            .thenReturn("image/png")
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("file3.png")))
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

        // file2 fails
        whenever(contentValidationService.detectContentType(any<InputStream>(), eq("file2.exe")))
            .thenReturn("application/x-msdownload")
        whenever(contentValidationService.validateContentType(eq(StorageDomain.AVATAR), eq("application/x-msdownload")))
            .thenThrow(ContentTypeNotAllowedException("Content type not allowed"))

        val result = storageService.batchUpload(workspaceId, StorageDomain.AVATAR, listOf(file1, file2, file3))

        assertEquals(3, result.results.size)
        assertEquals(2, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(201, result.results[0].status)
        assertEquals(415, result.results[1].status)
        assertEquals(201, result.results[2].status)
    }

    // ------ Batch Delete Tests ------

    @Test
    fun `batchDelete with 3 valid file IDs returns BatchDeleteResponse with 3 success results`() {
        val fileIds = (1..3).map { UUID.randomUUID() }
        fileIds.forEach { fileId ->
            val entity = StorageFactory.fileMetadataEntity(
                id = fileId,
                workspaceId = workspaceId,
                storageKey = "$workspaceId/avatar/$fileId.png"
            ).apply {
                createdAt = java.time.ZonedDateTime.now()
                updatedAt = java.time.ZonedDateTime.now()
            }
            whenever(fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId))
                .thenReturn(Optional.of(entity))
        }
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>())).thenAnswer { it.arguments[0] }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.batchDelete(workspaceId, BatchDeleteRequest(fileIds))

        assertEquals(3, result.succeeded)
        assertEquals(0, result.failed)
        assertEquals(3, result.results.size)
        result.results.forEach { item ->
            assertEquals(204, item.status)
            assertNull(item.error)
            assertNotNull(item.id)
        }
    }

    @Test
    fun `batchDelete with 1 valid and 1 nonexistent ID returns mixed results`() {
        val validId = UUID.randomUUID()
        val invalidId = UUID.randomUUID()

        val entity = StorageFactory.fileMetadataEntity(
            id = validId,
            workspaceId = workspaceId,
            storageKey = "$workspaceId/avatar/$validId.png"
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }
        whenever(fileMetadataRepository.findByIdAndWorkspaceId(validId, workspaceId))
            .thenReturn(Optional.of(entity))
        whenever(fileMetadataRepository.findByIdAndWorkspaceId(invalidId, workspaceId))
            .thenReturn(Optional.empty())
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>())).thenAnswer { it.arguments[0] }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.batchDelete(workspaceId, BatchDeleteRequest(listOf(validId, invalidId)))

        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(204, result.results[0].status)
        assertEquals(404, result.results[1].status)
        assertNotNull(result.results[1].error)
    }

    @Test
    fun `batchDelete with more than 50 IDs throws IllegalArgumentException`() {
        val fileIds = (1..51).map { UUID.randomUUID() }

        assertThrows<IllegalArgumentException> {
            storageService.batchDelete(workspaceId, BatchDeleteRequest(fileIds))
        }
    }

    @Test
    fun `batchDelete with empty list throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            storageService.batchDelete(workspaceId, BatchDeleteRequest(emptyList()))
        }
    }

    @Test
    fun `batchDelete individual item failure does not prevent other items from being processed`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()

        val entity1 = StorageFactory.fileMetadataEntity(id = id1, workspaceId = workspaceId, storageKey = "$workspaceId/avatar/$id1.png").apply {
            createdAt = java.time.ZonedDateTime.now(); updatedAt = java.time.ZonedDateTime.now()
        }
        val entity3 = StorageFactory.fileMetadataEntity(id = id3, workspaceId = workspaceId, storageKey = "$workspaceId/avatar/$id3.png").apply {
            createdAt = java.time.ZonedDateTime.now(); updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(fileMetadataRepository.findByIdAndWorkspaceId(id1, workspaceId)).thenReturn(Optional.of(entity1))
        whenever(fileMetadataRepository.findByIdAndWorkspaceId(id2, workspaceId)).thenReturn(Optional.empty())
        whenever(fileMetadataRepository.findByIdAndWorkspaceId(id3, workspaceId)).thenReturn(Optional.of(entity3))
        whenever(fileMetadataRepository.save(any<FileMetadataEntity>())).thenAnswer { it.arguments[0] }
        whenever(activityService.logActivity(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mock())

        val result = storageService.batchDelete(workspaceId, BatchDeleteRequest(listOf(id1, id2, id3)))

        assertEquals(3, result.results.size)
        assertEquals(2, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(204, result.results[0].status)
        assertEquals(404, result.results[1].status)
        assertEquals(204, result.results[2].status)
    }

    // ------ Helper ------

    private fun setupUploadMocks() {
        val savedEntity = StorageFactory.fileMetadataEntity(
            workspaceId = workspaceId,
            storageKey = testStorageKey,
            contentType = "image/png",
            fileSize = 1024L,
            uploadedBy = userId
        ).apply {
            createdAt = java.time.ZonedDateTime.now()
            updatedAt = java.time.ZonedDateTime.now()
        }

        whenever(contentValidationService.detectContentType(any<InputStream>(), any()))
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
    }

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

    // ------ Access Control Tests ------

    private val unauthorizedWorkspaceId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `uploadFile throws AccessDeniedException for unauthorized workspace`() {
        val file = mockMultipartFile("test.png", "image/png", ByteArray(100))

        assertThrows<org.springframework.security.access.AccessDeniedException> {
            storageService.uploadFile(unauthorizedWorkspaceId, StorageDomain.AVATAR, file)
        }
    }

    @Test
    fun `listFiles throws AccessDeniedException for unauthorized workspace`() {
        assertThrows<org.springframework.security.access.AccessDeniedException> {
            storageService.listFiles(unauthorizedWorkspaceId, null)
        }
    }

    @Test
    fun `getFile throws AccessDeniedException for unauthorized workspace`() {
        assertThrows<org.springframework.security.access.AccessDeniedException> {
            storageService.getFile(unauthorizedWorkspaceId, UUID.randomUUID())
        }
    }

    @Test
    fun `deleteFile throws AccessDeniedException for unauthorized workspace`() {
        assertThrows<org.springframework.security.access.AccessDeniedException> {
            storageService.deleteFile(unauthorizedWorkspaceId, UUID.randomUUID())
        }
    }

    @Test
    fun `generateSignedUrl throws AccessDeniedException for unauthorized workspace`() {
        assertThrows<org.springframework.security.access.AccessDeniedException> {
            storageService.generateSignedUrl(unauthorizedWorkspaceId, UUID.randomUUID(), null)
        }
    }

    @Test
    fun `confirmPresignedUpload throws AccessDeniedException for unauthorized workspace`() {
        val request = ConfirmUploadRequest(
            storageKey = "$unauthorizedWorkspaceId/avatar/${UUID.randomUUID()}.png",
            originalFilename = "test.png"
        )

        assertThrows<org.springframework.security.access.AccessDeniedException> {
            storageService.confirmPresignedUpload(unauthorizedWorkspaceId, request)
        }
    }

    // ------ IDOR Protection Tests ------

    @Test
    fun `confirmPresignedUpload rejects storage key belonging to different workspace`() {
        val otherWorkspaceId = UUID.randomUUID()
        val storageKey = "$otherWorkspaceId/avatar/${UUID.randomUUID()}.png"
        val request = ConfirmUploadRequest(storageKey = storageKey, originalFilename = "test.png")

        assertThrows<IllegalArgumentException> {
            storageService.confirmPresignedUpload(workspaceId, request)
        }

        verify(storageProvider, never()).exists(any())
    }
}
