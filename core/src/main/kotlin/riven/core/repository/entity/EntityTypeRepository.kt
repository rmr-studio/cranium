package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.entity.EntityTypeEntity
import riven.core.projection.entity.SemanticGroupProjection
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
     * Projection query to fetch only (id, semanticGroup) pairs without loading full entity rows.
     */
    @Query("SELECT e.id AS id, e.semanticGroup AS semanticGroup FROM EntityTypeEntity e WHERE e.id IN :ids")
    fun findSemanticGroupsByIds(@Param("ids") ids: Collection<UUID>): List<SemanticGroupProjection>
}
