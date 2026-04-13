package riven.core.repository.connector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import riven.core.entity.connector.CustomSourceTableMappingEntity
import java.util.UUID

/**
 * Spring Data JPA repository for [CustomSourceTableMappingEntity] (Phase 3 plan 03-01).
 *
 * Soft-delete filtering is automatic via `@SQLRestriction("deleted = false")`
 * on the concrete entity — derived queries here never need to repeat it.
 */
@Repository
interface CustomSourceTableMappingRepository : JpaRepository<CustomSourceTableMappingEntity, UUID> {

    fun findByConnectionId(connectionId: UUID): List<CustomSourceTableMappingEntity>

    fun findByConnectionIdAndTableName(
        connectionId: UUID,
        tableName: String,
    ): CustomSourceTableMappingEntity?

    fun findByEntityTypeId(entityTypeId: UUID): CustomSourceTableMappingEntity?
}
