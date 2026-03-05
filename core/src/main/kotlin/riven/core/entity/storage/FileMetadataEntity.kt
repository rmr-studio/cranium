package riven.core.entity.storage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.enums.storage.StorageDomain
import riven.core.models.storage.FileMetadata
import java.util.UUID

@Entity
@Table(
    name = "file_metadata",
    indexes = [
        Index(name = "idx_file_metadata_workspace_id", columnList = "workspace_id"),
        Index(name = "idx_file_metadata_workspace_domain", columnList = "workspace_id, domain"),
        Index(name = "uq_file_metadata_storage_key", columnList = "storage_key", unique = true),
    ]
)
data class FileMetadataEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false)
    val domain: StorageDomain,

    @Column(name = "storage_key", nullable = false, unique = true)
    val storageKey: String,

    @Column(name = "original_filename", nullable = false)
    val originalFilename: String,

    @Column(name = "content_type", nullable = false)
    val contentType: String,

    @Column(name = "file_size", nullable = false)
    val fileSize: Long,

    @Column(name = "uploaded_by", nullable = false, columnDefinition = "uuid")
    val uploadedBy: UUID,

) : AuditableSoftDeletableEntity() {

    fun toModel(): FileMetadata = FileMetadata(
        id = id!!,
        workspaceId = workspaceId,
        domain = domain,
        storageKey = storageKey,
        originalFilename = originalFilename,
        contentType = contentType,
        fileSize = fileSize,
        uploadedBy = uploadedBy,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!
    )
}
