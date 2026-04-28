package riven.core.repository.connector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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

    @Query("SELECT m FROM DataConnectorFieldMappingEntity m WHERE m.connectionId = :connectionId")
    fun findByConnectionId(@Param("connectionId") connectionId: UUID): List<DataConnectorFieldMappingEntity>

    @Query(
        "SELECT m FROM DataConnectorFieldMappingEntity m " +
            "WHERE m.connectionId = :connectionId AND m.tableName = :tableName"
    )
    fun findByConnectionIdAndTableName(
        @Param("connectionId") connectionId: UUID,
        @Param("tableName") tableName: String,
    ): List<DataConnectorFieldMappingEntity>
}
