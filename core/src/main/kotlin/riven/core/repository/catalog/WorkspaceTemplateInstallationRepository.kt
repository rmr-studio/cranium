package riven.core.repository.catalog

import org.springframework.data.jpa.repository.JpaRepository
import riven.core.entity.catalog.WorkspaceTemplateInstallationEntity
import java.util.*

interface WorkspaceTemplateInstallationRepository : JpaRepository<WorkspaceTemplateInstallationEntity, UUID> {

    fun findByWorkspaceIdAndManifestKey(workspaceId: UUID, manifestKey: String): WorkspaceTemplateInstallationEntity?

    fun findByWorkspaceId(workspaceId: UUID): List<WorkspaceTemplateInstallationEntity>

    fun findByWorkspaceIdAndManifestKeyIn(workspaceId: UUID, manifestKeys: List<String>): List<WorkspaceTemplateInstallationEntity>
}
