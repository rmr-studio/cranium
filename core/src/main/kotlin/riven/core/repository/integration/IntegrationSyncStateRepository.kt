package riven.core.repository.integration

import org.springframework.data.jpa.repository.JpaRepository
import riven.core.entity.integration.IntegrationSyncStateEntity
import java.util.*

/**
 * Repository for IntegrationSyncState — tracks per-connection per-entity-type sync progress.
 */
interface IntegrationSyncStateRepository : JpaRepository<IntegrationSyncStateEntity, UUID> {

    fun findByIntegrationConnectionId(integrationConnectionId: UUID): List<IntegrationSyncStateEntity>

    fun findByIntegrationConnectionIdAndEntityTypeId(
        integrationConnectionId: UUID,
        entityTypeId: UUID
    ): IntegrationSyncStateEntity?
}
