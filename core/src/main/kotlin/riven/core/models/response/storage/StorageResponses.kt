package riven.core.models.response.storage

import riven.core.models.storage.FileMetadata
import java.time.ZonedDateTime

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
