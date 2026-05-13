package cranium.core.repository.connector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import cranium.core.entity.connector.DataConnectorTableMappingEntity
import java.util.UUID

/**
 * Spring Data JPA repository for [DataConnectorTableMappingEntity] (Phase 3 plan 03-01).
 *
 * Soft-delete filtering is automatic via `@SQLRestriction("deleted = false")`
 * on the concrete entity — derived queries here never need to repeat it.
 */
@Repository
interface DataConnectorTableMappingRepository : JpaRepository<DataConnectorTableMappingEntity, UUID> {

    @Query("SELECT t FROM DataConnectorTableMappingEntity t WHERE t.connectionId = :connectionId")
    fun findByConnectionId(@Param("connectionId") connectionId: UUID): List<DataConnectorTableMappingEntity>

    @Query(
        "SELECT t FROM DataConnectorTableMappingEntity t " +
            "WHERE t.connectionId = :connectionId AND t.tableName = :tableName"
    )
    fun findByConnectionIdAndTableName(
        @Param("connectionId") connectionId: UUID,
        @Param("tableName") tableName: String,
    ): DataConnectorTableMappingEntity?

    @Query("SELECT t FROM DataConnectorTableMappingEntity t WHERE t.entityTypeId = :entityTypeId")
    fun findByEntityTypeId(@Param("entityTypeId") entityTypeId: UUID): DataConnectorTableMappingEntity?
}
