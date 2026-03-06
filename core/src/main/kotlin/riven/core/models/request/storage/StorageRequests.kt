package riven.core.models.request.storage

import riven.core.enums.storage.StorageDomain
import java.util.UUID

data class GenerateSignedUrlRequest(
    val fileId: UUID,
    val expiresInSeconds: Long? = null
)

data class PresignedUploadRequest(
    val domain: StorageDomain,
    val contentType: String? = null
)

data class ConfirmUploadRequest(
    val storageKey: String,
    val originalFilename: String,
    val metadata: Map<String, String>? = null
)

data class BatchDeleteRequest(
    val fileIds: List<UUID>
)

data class UpdateMetadataRequest(
    val metadata: Map<String, String?>
)
