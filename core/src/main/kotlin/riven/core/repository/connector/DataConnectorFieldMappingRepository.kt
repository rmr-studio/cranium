package riven.core.repository.connector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import riven.core.entity.connector.DataConnectorFieldMappingEntity
import java.util.UUID

/**
 * Spring Data JPA repository for [DataConnectorFieldMappingEntity] (Phase 3 plan 03-01).
 *
 * Soft-delete filtering is automatic via `@SQLRestriction("deleted = false")`
 * on the concrete entity.
 */
@Repository
interface DataConnectorFieldMappingRepository : JpaRepository<DataConnectorFieldMappingEntity, UUID> {

    fun findByConnectionId(connectionId: UUID): List<DataConnectorFieldMappingEntity>

    fun findByConnectionIdAndTableName(
        connectionId: UUID,
        tableName: String,
    ): List<DataConnectorFieldMappingEntity>
}
