package cranium.core.repository.connector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import cranium.core.entity.connector.DataConnectorConnectionEntity
import java.util.Optional
import java.util.UUID

/**
 * Spring Data JPA repository for [DataConnectorConnectionEntity].
 *
 * Soft-delete filtering is automatic via `@SQLRestriction("deleted = false")`
 * on [cranium.core.entity.util.AuditableSoftDeletableEntity] — derived queries
 * here never need to repeat `AND deleted = false`.
 */
@Repository
interface DataConnectorConnectionRepository : JpaRepository<DataConnectorConnectionEntity, UUID> {

    fun findByWorkspaceId(workspaceId: UUID): List<DataConnectorConnectionEntity>

    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<DataConnectorConnectionEntity>
}
