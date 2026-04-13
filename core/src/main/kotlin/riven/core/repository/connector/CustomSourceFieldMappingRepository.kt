package riven.core.repository.connector

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import riven.core.entity.connector.CustomSourceFieldMappingEntity
import java.util.UUID

/**
 * Spring Data JPA repository for [CustomSourceFieldMappingEntity] (Phase 3 plan 03-01).
 *
 * Soft-delete filtering is automatic via `@SQLRestriction("deleted = false")`
 * on the concrete entity.
 */
@Repository
interface CustomSourceFieldMappingRepository : JpaRepository<CustomSourceFieldMappingEntity, UUID> {

    fun findByConnectionId(connectionId: UUID): List<CustomSourceFieldMappingEntity>

    fun findByConnectionIdAndTableName(
        connectionId: UUID,
        tableName: String,
    ): List<CustomSourceFieldMappingEntity>
}
