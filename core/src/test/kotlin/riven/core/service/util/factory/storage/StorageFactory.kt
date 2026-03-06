package riven.core.service.util.factory.storage

import riven.core.entity.storage.FileMetadataEntity
import riven.core.enums.storage.StorageDomain
import riven.core.models.storage.FileMetadata
import java.time.ZonedDateTime
import java.util.UUID

object StorageFactory {

    fun fileMetadataEntity(
        id: UUID? = UUID.randomUUID(),
        workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210"),
        domain: StorageDomain = StorageDomain.AVATAR,
        storageKey: String = "$workspaceId/avatar/${UUID.randomUUID()}.png",
        originalFilename: String = "test-image.png",
        contentType: String = "image/png",
        fileSize: Long = 1024L,
        uploadedBy: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789"),
        metadata: Map<String, String>? = null
    ): FileMetadataEntity = FileMetadataEntity(
        id = id,
        workspaceId = workspaceId,
        domain = domain,
        storageKey = storageKey,
        originalFilename = originalFilename,
        contentType = contentType,
        fileSize = fileSize,
        uploadedBy = uploadedBy,
        metadata = metadata
    )

    fun fileMetadata(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef9876543210"),
        domain: StorageDomain = StorageDomain.AVATAR,
        storageKey: String = "$workspaceId/avatar/${UUID.randomUUID()}.png",
        originalFilename: String = "test-image.png",
        contentType: String = "image/png",
        fileSize: Long = 1024L,
        uploadedBy: UUID = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789"),
        metadata: Map<String, String>? = null,
        createdAt: ZonedDateTime = ZonedDateTime.now(),
        updatedAt: ZonedDateTime = ZonedDateTime.now()
    ): FileMetadata = FileMetadata(
        id = id,
        workspaceId = workspaceId,
        domain = domain,
        storageKey = storageKey,
        originalFilename = originalFilename,
        contentType = contentType,
        fileSize = fileSize,
        uploadedBy = uploadedBy,
        metadata = metadata,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
