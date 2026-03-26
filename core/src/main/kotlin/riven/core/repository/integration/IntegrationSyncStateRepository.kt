package riven.core.repository.integration

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import riven.core.entity.integration.IntegrationSyncStateEntity
import java.util.*

/**
 * Repository for IntegrationSyncState — tracks per-connection per-entity-type sync progress.
 */
interface IntegrationSyncStateRepository : JpaRepository<IntegrationSyncStateEntity, UUID> {

    @Query("SELECT s FROM IntegrationSyncStateEntity s WHERE s.integrationConnectionId = :connectionId")
    fun findByIntegrationConnectionId(connectionId: UUID): List<IntegrationSyncStateEntity>

    @Query("SELECT s FROM IntegrationSyncStateEntity s WHERE s.integrationConnectionId = :connectionId AND s.entityTypeId = :entityTypeId")
    fun findByIntegrationConnectionIdAndEntityTypeId(
        connectionId: UUID,
        entityTypeId: UUID
    ): IntegrationSyncStateEntity?
}
