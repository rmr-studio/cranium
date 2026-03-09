package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.entity.EntityTypeEntity
import java.util.*

/**
 * Repository for EntityType entities.
 */
interface EntityTypeRepository : JpaRepository<EntityTypeEntity, UUID> {

    fun findByworkspaceId(id: UUID): List<EntityTypeEntity>


    /**
     * Find entity type by workspace and key.
     * Returns the single matching entity type (mutable pattern - only one row per org+key).
     */
    fun findByworkspaceIdAndKey(
        workspaceId: UUID,
        key: String
    ): Optional<EntityTypeEntity>

    fun findByworkspaceIdAndKeyIn(
        workspaceId: UUID,
        keys: List<String>
    ): List<EntityTypeEntity>

    /**
     * Find soft-deleted entity types by workspace and keys.
     * Uses native query to bypass @SQLRestriction("deleted = false").
     */
    @Query(
        "SELECT * FROM entity_types WHERE workspace_id = :workspaceId AND key IN (:keys) AND deleted = true",
        nativeQuery = true
    )
    fun findSoftDeletedByWorkspaceIdAndKeyIn(
        @Param("workspaceId") workspaceId: UUID,
        @Param("keys") keys: List<String>
    ): List<EntityTypeEntity>

}
