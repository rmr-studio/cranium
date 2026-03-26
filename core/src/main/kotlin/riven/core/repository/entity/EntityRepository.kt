package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import riven.core.entity.entity.EntityEntity
import java.util.*

/**
 * Repository for Entity instances.
 */
interface EntityRepository : JpaRepository<EntityEntity, UUID> {

    @Query("SELECT e FROM EntityEntity e WHERE e.id in :ids")
    fun findByIdIn(ids: Collection<UUID>): List<EntityEntity>

    /**
     * Find all entities for an workspace.
     */
    @Query("SELECT e FROM EntityEntity e WHERE e.workspaceId = :workspaceId")
    fun findByWorkspaceId(workspaceId: UUID): List<EntityEntity>

    @Query("SELECT e FROM EntityEntity e WHERE e.typeId = :typeId")
    fun findByTypeId(typeId: UUID): List<EntityEntity>

    @Query(
        """
       SELECT e FROM EntityEntity e
            WHERE e.typeId in :typeIds
    """
    )
    fun findByTypeIdIn(typeIds: List<UUID>): List<EntityEntity>

    @Query("SELECT e FROM EntityEntity e WHERE e.id = :id AND e.workspaceId = :workspaceId")
    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<EntityEntity>

    @Modifying
    @Query(
        value = """
        UPDATE entities
        SET deleted = true, deleted_at = CURRENT_TIMESTAMP
        WHERE id = ANY(:ids)
          AND workspace_id = :workspaceId
          AND deleted = false
        RETURNING *
    """, nativeQuery = true
    )
    fun deleteByIds(ids: Array<UUID>, workspaceId: UUID): List<EntityEntity>

    @Query("""
        SELECT e FROM EntityEntity e
        WHERE e.workspaceId = :workspaceId
          AND e.sourceIntegrationId = :sourceIntegrationId
          AND e.sourceExternalId IN :sourceExternalIds
    """)
    fun findByWorkspaceIdAndSourceIntegrationIdAndSourceExternalIdIn(
        workspaceId: UUID,
        sourceIntegrationId: UUID,
        sourceExternalIds: Collection<String>
    ): List<EntityEntity>

}
