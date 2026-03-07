package riven.core.models.response.storage

import riven.core.models.storage.FileMetadata
import java.time.ZonedDateTime
import java.util.UUID

data class UploadFileResponse(
    val file: FileMetadata,
    val signedUrl: String
)

data class FileListResponse(
    val files: List<FileMetadata>
)

data class SignedUrlResponse(
    val url: String,
    val expiresAt: ZonedDateTime
)

data class PresignedUploadResponse(
    val storageKey: String,
    val uploadUrl: String?,
    val method: String?,
    val supported: Boolean
)

data class BatchItemResult(
    val id: UUID?,
    val filename: String?,
    val status: Int,
    val error: String?
)

data class BatchUploadResponse(
    val results: List<BatchItemResult>,
    val succeeded: Int,
    val failed: Int
)

data class BatchDeleteResponse(
    val results: List<BatchItemResult>,
    val succeeded: Int,
    val failed: Int
)
