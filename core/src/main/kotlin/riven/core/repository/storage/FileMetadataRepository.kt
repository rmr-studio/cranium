package riven.core.repository.storage

import org.springframework.data.jpa.repository.JpaRepository
import riven.core.entity.storage.FileMetadataEntity
import riven.core.enums.storage.StorageDomain
import java.util.Optional
import java.util.UUID

interface FileMetadataRepository : JpaRepository<FileMetadataEntity, UUID> {

    fun findByWorkspaceIdAndDomain(workspaceId: UUID, domain: StorageDomain): List<FileMetadataEntity>

    fun findByWorkspaceId(workspaceId: UUID): List<FileMetadataEntity>

    fun findByStorageKey(storageKey: String): Optional<FileMetadataEntity>

    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<FileMetadataEntity>
}
