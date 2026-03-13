package riven.core.service.storage

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import riven.core.configuration.storage.StorageConfigurationProperties
import riven.core.entity.storage.FileMetadataEntity
import riven.core.enums.activity.Activity
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.storage.StorageDomain
import riven.core.enums.util.OperationType
import riven.core.exceptions.ContentTypeNotAllowedException
import riven.core.exceptions.SignedUrlExpiredException
import riven.core.exceptions.StorageNotFoundException
import riven.core.models.request.storage.BatchDeleteRequest
import riven.core.models.request.storage.ConfirmUploadRequest
import riven.core.models.request.storage.UpdateMetadataRequest
import riven.core.exceptions.FileSizeLimitExceededException
import riven.core.exceptions.NotFoundException
import riven.core.models.response.storage.BatchDeleteResponse
import riven.core.models.response.storage.BatchItemResult
import riven.core.models.response.storage.BatchUploadResponse
import riven.core.models.response.storage.FileListResponse
import riven.core.models.response.storage.PresignedUploadResponse
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
    private val storageConfig: StorageConfigurationProperties,
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
    fun uploadFile(
        workspaceId: UUID,
        domain: StorageDomain,
        file: MultipartFile,
        metadata: Map<String, String>? = null
    ): UploadFileResponse = uploadFileInternal(workspaceId, domain, file, metadata)

    /**
     * Upload a file without workspace access check. Used during onboarding when the
     * workspace role is not yet in the JWT.
     */
    internal fun uploadFileInternal(
        workspaceId: UUID,
        domain: StorageDomain,
        file: MultipartFile,
        metadata: Map<String, String>? = null
    ): UploadFileResponse {
        val userId = authTokenService.getUserId()
        metadata?.run { validateMetadata(this) }

        val detectedType = detectAndValidate(file.bytes, file.originalFilename, domain)
        val content = sanitizeIfSvg(file.bytes, detectedType)
        val storageKey = contentValidationService.generateStorageKey(workspaceId, domain, detectedType)

        storeFile(storageKey, content, detectedType)
        return try {
            val fileMetadata = persistMetadata(workspaceId, domain, storageKey, file.originalFilename ?: "unknown", detectedType, content.size.toLong(), userId, metadata)
            logUploadActivity(userId, workspaceId, fileMetadata)
            val signedUrl = generateProviderSignedUrl(storageKey, signedUrlService.getDefaultExpiry())
            UploadFileResponse(fileMetadata, signedUrl)
        } catch (e: Exception) {
            try {
                storageProvider.delete(storageKey)
            } catch (deleteError: Exception) {
                logger.error(deleteError) { "Failed to clean up uploaded file '$storageKey' after post-upload failure" }
            }
            throw e
        }
    }

    /**
     * Upload a file scoped to a user rather than a workspace.
     *
     * Performs content validation and storage but does not persist FileMetadata
     * (which requires workspace scope) or log workspace-scoped activity.
     *
     * @param userId user scope for storage key generation
     * @param domain storage domain controlling validation rules
     * @param file uploaded multipart file
     * @return the permanent storage key for the uploaded file
     */
    fun uploadUserFile(userId: UUID, domain: StorageDomain, file: MultipartFile): String {
        val detectedType = detectAndValidate(file.bytes, file.originalFilename, domain)
        val content = sanitizeIfSvg(file.bytes, detectedType)
        val storageKey = contentValidationService.generateUserStorageKey(userId, domain, detectedType)

        storeFile(storageKey, content, detectedType)
        logger.info { "Uploaded user file: storageKey=$storageKey, userId=$userId, domain=$domain" }
        return storageKey
    }

    // ------ Presigned Upload ------

    /**
     * Request a presigned upload URL for direct-to-provider upload.
     *
     * Returns a presigned URL and storage key for providers that support it (S3, Supabase).
     * For local provider, returns supported=false.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun requestPresignedUpload(workspaceId: UUID, domain: StorageDomain, contentType: String?): PresignedUploadResponse {
        val userId = authTokenService.getUserId()
        val effectiveContentType = contentType ?: "application/octet-stream"
        val storageKey = contentValidationService.generateStorageKey(workspaceId, domain, effectiveContentType)
        val expiry = Duration.ofSeconds(storageConfig.presignedUpload.expirySeconds)

        return try {
            val uploadUrl = storageProvider.generateUploadUrl(storageKey, effectiveContentType, expiry)
            PresignedUploadResponse(storageKey = storageKey, uploadUrl = uploadUrl, method = "PUT", supported = true)
        } catch (e: UnsupportedOperationException) {
            logger.debug { "Presigned upload not supported by provider, returning unsupported response" }
            PresignedUploadResponse(storageKey = storageKey, uploadUrl = null, method = null, supported = false)
        }
    }

    /**
     * Confirm a presigned upload by verifying the file exists, validating content, and persisting metadata.
     *
     * Downloads the file to validate content type via Tika. If validation fails,
     * the file is deleted from the provider before throwing.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun confirmPresignedUpload(workspaceId: UUID, request: ConfirmUploadRequest): UploadFileResponse {
        val userId = authTokenService.getUserId()

        validateStorageKeyWorkspace(request.storageKey, workspaceId)

        if (!storageProvider.exists(request.storageKey)) {
            throw StorageNotFoundException("File not found at storage key: ${request.storageKey}")
        }

        val downloadResult = storageProvider.download(request.storageKey)
        val domain = parseDomainFromStorageKey(request.storageKey)

        // Early size gate — reject before buffering into memory
        if (downloadResult.contentLength > 0) {
            try {
                contentValidationService.validateFileSize(domain, downloadResult.contentLength)
            } catch (e: FileSizeLimitExceededException) {
                downloadResult.content.close()
                deletePhysicalFile(request.storageKey)
                throw e
            }
        }

        val bytes = downloadResult.content.use { it.readBytes() }
        val detectedType = contentValidationService.detectContentType(ByteArrayInputStream(bytes), request.originalFilename)

        validatePresignedUploadContent(request.storageKey, domain, detectedType, bytes.size.toLong())

        request.metadata?.let { validateMetadata(it) }

        val fileMetadata = persistMetadata(workspaceId, domain, request.storageKey, request.originalFilename, detectedType, bytes.size.toLong(), userId, request.metadata)
        logUploadActivity(userId, workspaceId, fileMetadata)

        val signedUrl = generateProviderSignedUrl(request.storageKey, signedUrlService.getDefaultExpiry())
        return UploadFileResponse(fileMetadata, signedUrl)
    }

    // ------ Metadata ------

    /**
     * Update custom metadata on a file with merge semantics.
     *
     * New keys are added, existing keys are updated, keys with null values are removed.
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun updateMetadata(workspaceId: UUID, fileId: UUID, request: UpdateMetadataRequest): FileMetadata {
        val userId = authTokenService.getUserId()
        val entity = findOrThrow { fileMetadataRepository.findByIdAndWorkspaceId(fileId, workspaceId) }

        validateMetadataUpdate(request.metadata)

        val previousMetadata = entity.metadata?.toMap()
        val merged = mergeMetadata(entity.metadata, request.metadata)
        require(merged.size <= 20) { "Metadata cannot have more than 20 key-value pairs after merge" }
        entity.metadata = if (merged.isEmpty()) null else merged
        fileMetadataRepository.save(entity)

        logMetadataUpdateActivity(userId, workspaceId, entity, previousMetadata)

        return entity.toModel()
    }

    // ------ Batch Operations ------

    /**
     * Upload multiple files in a single batch with per-item success/failure results.
     *
     * Each file is processed independently -- a failure in one does not prevent others
     * from being processed. Not annotated with @Transactional to ensure each item
     * commits independently.
     *
     * @param workspaceId workspace scope
     * @param domain storage domain controlling validation rules
     * @param files list of multipart files (max 10)
     * @return batch response with per-item results and succeeded/failed counts
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun batchUpload(workspaceId: UUID, domain: StorageDomain, files: List<MultipartFile>): BatchUploadResponse {
        require(files.isNotEmpty()) { "At least one file is required" }
        require(files.size <= 10) { "Maximum 10 files per batch upload" }

        val results = files.map { file ->
            try {
                val response = uploadFile(workspaceId, domain, file)
                BatchItemResult(id = response.file.id, filename = file.originalFilename, status = 201, error = null)
            } catch (e: ContentTypeNotAllowedException) {
                BatchItemResult(id = null, filename = file.originalFilename, status = 415, error = e.message)
            } catch (e: FileSizeLimitExceededException) {
                BatchItemResult(id = null, filename = file.originalFilename, status = 413, error = e.message)
            } catch (e: Exception) {
                logger.error(e) { "Batch upload failed for file: ${file.originalFilename}" }
                BatchItemResult(id = null, filename = file.originalFilename, status = 500, error = "Upload failed: ${e.message}")
            }
        }

        return BatchUploadResponse(results, results.count { it.error == null }, results.count { it.error != null })
    }

    /**
     * Delete multiple files in a single batch with per-item success/failure results.
     *
     * Each file ID is processed independently -- a failure in one does not prevent others
     * from being processed. Not annotated with @Transactional to ensure each item
     * commits independently.
     *
     * @param workspaceId workspace scope
     * @param request batch delete request containing file IDs (max 50)
     * @return batch response with per-item results and succeeded/failed counts
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun batchDelete(workspaceId: UUID, request: BatchDeleteRequest): BatchDeleteResponse {
        require(request.fileIds.isNotEmpty()) { "At least one file ID is required" }
        require(request.fileIds.size <= 50) { "Maximum 50 files per batch delete" }

        val results = request.fileIds.map { fileId ->
            try {
                deleteFile(workspaceId, fileId)
                BatchItemResult(id = fileId, filename = null, status = 204, error = null)
            } catch (e: NotFoundException) {
                BatchItemResult(id = fileId, filename = null, status = 404, error = e.message)
            } catch (e: Exception) {
                logger.error(e) { "Batch delete failed for file: $fileId" }
                BatchItemResult(id = fileId, filename = null, status = 500, error = "Delete failed: ${e.message}")
            }
        }

        return BatchDeleteResponse(results, results.count { it.error == null }, results.count { it.error != null })
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
        val url = generateProviderSignedUrl(entity.storageKey, expiry)
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

    /**
     * Try provider-native signed URL generation first; fall back to HMAC-based SignedUrlService
     * when the provider does not support it (e.g. local filesystem adapter).
     */
    private fun generateProviderSignedUrl(storageKey: String, expiry: Duration): String {
        return try {
            storageProvider.generateSignedUrl(storageKey, expiry)
        } catch (e: UnsupportedOperationException) {
            signedUrlService.generateDownloadUrl(storageKey, expiry)
        }
    }

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
        uploadedBy: UUID,
        metadata: Map<String, String>? = null
    ): FileMetadata {
        val entity = FileMetadataEntity(
            workspaceId = workspaceId,
            domain = domain,
            storageKey = storageKey,
            originalFilename = originalFilename,
            contentType = contentType,
            fileSize = fileSize,
            uploadedBy = uploadedBy,
            metadata = metadata
        )
        return fileMetadataRepository.save(entity).toModel()
    }

    private fun parseDomainFromStorageKey(storageKey: String): StorageDomain {
        val parts = storageKey.split("/")
        require(parts.size >= 3) { "Invalid storage key format: $storageKey" }
        val domainString = parts[1].uppercase()
        return try {
            StorageDomain.valueOf(domainString)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown domain in storage key: $domainString")
        }
    }

    private fun validateStorageKeyWorkspace(storageKey: String, workspaceId: UUID) {
        val parts = storageKey.split("/")
        require(parts.isNotEmpty()) { "Invalid storage key format: $storageKey" }
        val keyWorkspaceId = try {
            UUID.fromString(parts[0])
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Storage key does not contain a valid workspace ID: $storageKey")
        }
        require(keyWorkspaceId == workspaceId) {
            "Storage key does not belong to workspace $workspaceId"
        }
    }

    private fun validatePresignedUploadContent(storageKey: String, domain: StorageDomain, detectedType: String, fileSize: Long) {
        try {
            contentValidationService.validateContentType(domain, detectedType)
            contentValidationService.validateFileSize(domain, fileSize)
        } catch (e: Exception) {
            when (e) {
                is ContentTypeNotAllowedException, is FileSizeLimitExceededException -> {
                    try {
                        storageProvider.delete(storageKey)
                    } catch (deleteError: Exception) {
                        logger.error(deleteError) { "Failed to delete invalid file '$storageKey' from provider after validation rejection" }
                    }
                    throw e
                }
                else -> throw e
            }
        }
    }

    private fun mergeMetadata(existing: Map<String, String>?, patch: Map<String, String?>): Map<String, String> {
        val merged = existing?.toMutableMap() ?: mutableMapOf()
        patch.forEach { (key, value) ->
            if (value == null) merged.remove(key) else merged[key] = value
        }
        return merged
    }

    private fun validateMetadata(metadata: Map<String, String>) {
        val keyPattern = Regex("^[a-zA-Z0-9_-]{1,64}$")
        require(metadata.size <= 20) { "Metadata cannot have more than 20 key-value pairs" }
        metadata.forEach { (key, value) ->
            require(keyPattern.matches(key)) { "Metadata key '$key' must match pattern ^[a-zA-Z0-9_-]{1,64}$" }
            require(value.length <= 1024) { "Metadata value for key '$key' exceeds maximum length of 1024 characters" }
        }
    }

    private fun validateMetadataUpdate(metadata: Map<String, String?>) {
        val keyPattern = Regex("^[a-zA-Z0-9_-]{1,64}$")
        val nonNullEntries = metadata.filterValues { it != null }
        require(nonNullEntries.size <= 20) { "Metadata cannot have more than 20 non-null key-value pairs" }
        metadata.forEach { (key, value) ->
            require(keyPattern.matches(key)) { "Metadata key '$key' must match pattern ^[a-zA-Z0-9_-]{1,64}$" }
            if (value != null) {
                require(value.length <= 1024) { "Metadata value for key '$key' exceeds maximum length of 1024 characters" }
            }
        }
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

    private fun logMetadataUpdateActivity(
        userId: UUID,
        workspaceId: UUID,
        entity: FileMetadataEntity,
        previousMetadata: Map<String, String>?
    ) {
        activityService.log(
            activity = Activity.FILE_UPDATE,
            operation = OperationType.UPDATE,
            userId = userId,
            workspaceId = workspaceId,
            entityType = ApplicationEntityType.FILE,
            entityId = entity.id,
            "previousMetadata" to previousMetadata,
            "updatedMetadata" to entity.metadata
        )
    }
}
