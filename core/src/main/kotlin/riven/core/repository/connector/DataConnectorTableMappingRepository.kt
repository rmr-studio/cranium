package riven.core.repository.connector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import riven.core.entity.connector.DataConnectorTableMappingEntity
import java.util.UUID

/**
 * Spring Data JPA repository for [DataConnectorTableMappingEntity] (Phase 3 plan 03-01).
 *
 * Soft-delete filtering is automatic via `@SQLRestriction("deleted = false")`
 * on the concrete entity — derived queries here never need to repeat it.
 */
@Repository
interface DataConnectorTableMappingRepository : JpaRepository<DataConnectorTableMappingEntity, UUID> {

    fun findByConnectionId(connectionId: UUID): List<DataConnectorTableMappingEntity>

    fun findByConnectionIdAndTableName(
        connectionId: UUID,
        tableName: String,
    ): DataConnectorTableMappingEntity?

    fun findByEntityTypeId(entityTypeId: UUID): DataConnectorTableMappingEntity?
}
