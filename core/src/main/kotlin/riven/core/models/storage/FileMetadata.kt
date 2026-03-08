package riven.core.models.storage

import riven.core.enums.storage.StorageDomain
import java.time.ZonedDateTime
import java.util.UUID

data class FileMetadata(
    val id: UUID,
    val workspaceId: UUID,
    val domain: StorageDomain,
    val storageKey: String,
    val originalFilename: String,
    val contentType: String,
    val fileSize: Long,
    val uploadedBy: UUID,
    val metadata: Map<String, String>? = null,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime
)
