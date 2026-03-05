package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import riven.core.entity.storage.FileMetadataEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.storage.StorageDomain
import riven.core.enums.util.OperationType
import riven.core.exceptions.SignedUrlExpiredException
import riven.core.models.response.storage.FileListResponse
import riven.core.models.response.storage.SignedUrlResponse
import riven.core.models.response.storage.UploadFileResponse
import riven.core.models.storage.DownloadResult
import riven.core.models.storage.FileMetadata
import riven.core.models.storage.StorageProvider
import riven.core.repository.storage.FileMetadataRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.util.ServiceUtil.findOrThrow
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Orchestrates all storage operations: upload, download, delete, and list.
 *
 * Coordinates content validation, provider I/O, metadata persistence,
 * signed URL generation, and activity logging.
 */
@Service
class StorageService(
    private val logger: KLogger,
    private val storageProvider: StorageProvider,
    private val contentValidationService: ContentValidationService,
    private val signedUrlService: SignedUrlService,
    private val fileMetadataRepository: FileMetadataRepository,
    private val activityService: ActivityService,
    private val authTokenService: AuthTokenService
) {

    // ------ Upload ------

    /**
     * Upload a file with content validation, storage, metadata persistence, and activity logging.
     *
     * Flow: detect MIME type -> validate type/size -> sanitize SVG if needed -> store ->
     * persist metadata -> log activity -> return with signed download URL.
     *
     * @param workspaceId workspace scope
     * @param domain storage domain controlling validation rules
     * @param file uploaded multipart file
     * @return upload response with file metadata and signed download URL
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun uploadFile(workspaceId: UUID, domain: StorageDomain, file: MultipartFile): UploadFileResponse {
        val userId = authTokenService.getUserId()
        val bytes = file.bytes

        val detectedType = detectAndValidate(bytes, file.originalFilename, domain)
        val content = sanitizeIfSvg(bytes, detectedType)
        val storageKey = contentValidationService.generateStorageKey(workspaceId, domain, detectedType)

        storeFile(storageKey, content, detectedType)
        val metadata = persistMetadata(workspaceId, domain, storageKey, file.originalFilename ?: "unknown", detectedType, content.size.toLong(), userId)
        logUploadActivity(userId, workspaceId, metadata)

        val signedUrl = signedUrlService.generateDownloadUrl(storageKey, signedUrlService.getDefaultExpiry())
        return UploadFileResponse(metadata, signedUrl)
    }

    // ------ Read ------

    /**
     * Get file metadata by ID within a workspace.
     *
     * @throws riven.core.exceptions.NotFoundException if file does not exist in this workspace
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun getFile(workspaceId: UUID, fileId: UUID): FileMetadata =
        findOrThrow { fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId) }.toModel()

    /**
     * Generate a signed download URL for a file.
     *
     * @param expiresInSeconds custom expiry in seconds, or null for default
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun generateSignedUrl(workspaceId: UUID, fileId: UUID, expiresInSeconds: Long?): SignedUrlResponse {
        val entity = findOrThrow { fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId) }
        val expiry = expiresInSeconds?.let { Duration.ofSeconds(it) } ?: signedUrlService.getDefaultExpiry()
        val url = signedUrlService.generateDownloadUrl(entity.storageKey, expiry)
        val expiresAt = ZonedDateTime.now().plus(expiry)

        return SignedUrlResponse(url, expiresAt)
    }

    // ------ Delete ------

    /**
     * Soft-delete file metadata, then remove the physical file from the provider.
     *
     * If physical deletion fails, the soft-delete is preserved and the error is logged.
     * This ensures metadata consistency even when the provider is temporarily unavailable.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun deleteFile(workspaceId: UUID, fileId: UUID) {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId) }

        softDeleteMetadata(entity)
        deletePhysicalFile(entity.storageKey)
        logDeleteActivity(userId, workspaceId, entity)
    }

    // ------ List ------

    /**
     * List files in a workspace, optionally filtered by domain.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun listFiles(workspaceId: UUID, domain: StorageDomain?): FileListResponse {
        val entities = if (domain != null) {
            fileMetadataRepository.findByWorkspaceIdAndDomain(workspaceId, domain)
        } else {
            fileMetadataRepository.findByWorkspaceId(workspaceId)
        }

        return FileListResponse(entities.map { it.toModel() })
    }

    // ------ Download (token-authorized) ------

    /**
     * Download a file using a signed token for authorization.
     *
     * This method does NOT have @PreAuthorize -- the signed token IS the authorization.
     *
     * @throws SignedUrlExpiredException if the token is invalid or expired
     */
    fun downloadFile(token: String): DownloadResult {
        val (storageKey, _) = signedUrlService.validateToken(token)
            ?: throw SignedUrlExpiredException("Signed URL is invalid or expired")

        val providerResult = storageProvider.download(storageKey)
        val originalFilename = fileMetadataRepository.findByStorageKey(storageKey)
            .map { it.originalFilename }
            .orElse(null)

        return DownloadResult(
            content = providerResult.content,
            contentType = providerResult.contentType,
            contentLength = providerResult.contentLength,
            originalFilename = originalFilename
        )
    }

    // ------ Private Helpers ------

    private fun detectAndValidate(bytes: ByteArray, originalFilename: String?, domain: StorageDomain): String {
        val detectedType = contentValidationService.detectContentType(ByteArrayInputStream(bytes), originalFilename)
        contentValidationService.validateContentType(domain, detectedType)
        contentValidationService.validateFileSize(domain, bytes.size.toLong())
        return detectedType
    }

    private fun sanitizeIfSvg(bytes: ByteArray, contentType: String): ByteArray =
        if (contentType == "image/svg+xml") {
            contentValidationService.sanitizeSvg(bytes)
        } else {
            bytes
        }

    private fun storeFile(storageKey: String, content: ByteArray, contentType: String) {
        storageProvider.upload(storageKey, ByteArrayInputStream(content), contentType, content.size.toLong())
    }

    private fun persistMetadata(
        workspaceId: UUID,
        domain: StorageDomain,
        storageKey: String,
        originalFilename: String,
        contentType: String,
        fileSize: Long,
        uploadedBy: UUID
    ): FileMetadata {
        val entity = FileMetadataEntity(
            workspaceId = workspaceId,
            domain = domain,
            storageKey = storageKey,
            originalFilename = originalFilename,
            contentType = contentType,
            fileSize = fileSize,
            uploadedBy = uploadedBy
        )
        return fileMetadataRepository.save(entity).toModel()
    }

    private fun softDeleteMetadata(entity: FileMetadataEntity) {
        entity.deleted = true
        entity.deletedAt = ZonedDateTime.now()
        fileMetadataRepository.save(entity)
    }

    private fun deletePhysicalFile(storageKey: String) {
        try {
            storageProvider.delete(storageKey)
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete physical file '$storageKey' from provider. Metadata is already soft-deleted." }
        }
    }

    private fun logUploadActivity(userId: UUID, workspaceId: UUID, metadata: FileMetadata) {
        activityService.log(
            activity = Activity.FILE_UPLOAD,
            operation = OperationType.CREATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.FILE,
            entityId = metadata.id,
            "filename" to metadata.originalFilename,
            "contentType" to metadata.contentType,
            "fileSize" to metadata.fileSize
        )
    }

    private fun logDeleteActivity(userId: UUID, workspaceId: UUID, entity: FileMetadataEntity) {
        activityService.log(
            activity = Activity.FILE_DELETE,
            operation = OperationType.DELETE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.FILE,
            entityId = entity.id,
            "filename" to entity.originalFilename,
            "storageKey" to entity.storageKey
        )
    }
}
