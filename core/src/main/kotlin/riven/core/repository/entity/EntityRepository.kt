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

    @Query("SELECT e FROM EntityEntity e WHERE e.id IN :ids AND e.workspaceId = :workspaceId")
    fun findByIdInAndWorkspaceId(ids: Collection<UUID>, workspaceId: UUID): List<EntityEntity>

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

    /**
     * Find a single entity by its source external ID within a workspace.
     *
     * Used by [riven.core.service.identity.IdentityLookupService] to resolve entities
     * by their integration-sourced external identifier.
     */
    @Query("""
        SELECT e FROM EntityEntity e
        WHERE e.workspaceId = :workspaceId
          AND e.sourceExternalId = :sourceExternalId
    """)
    fun findByWorkspaceIdAndSourceExternalId(
        workspaceId: UUID,
        sourceExternalId: String,
    ): List<EntityEntity>

    /**
     * Find all entities of a given type-key within a workspace, ordered newest-first.
     * Used by the knowledge projector layer (notes, glossary) for entity-backed reads.
     */
    @Query("""
        SELECT e FROM EntityEntity e
        WHERE e.workspaceId = :workspaceId
          AND e.typeKey = :typeKey
        ORDER BY e.createdAt DESC, e.id DESC
    """)
    fun findByWorkspaceIdAndTypeKey(workspaceId: UUID, typeKey: String): List<EntityEntity>

    /**
     * Count entities of a given type-key within a workspace. Used by parity tests
     * and projector listings.
     */
    @Query("""
        SELECT COUNT(e) FROM EntityEntity e
        WHERE e.workspaceId = :workspaceId
          AND e.typeKey = :typeKey
    """)
    fun countByWorkspaceIdAndTypeKey(workspaceId: UUID, typeKey: String): Long

    /**
     * Batch sourceExternalId match on a specific entity type within a workspace.
     * Used by the projection pipeline for identity resolution (Check 1).
     */
    @Query("""
        SELECT e FROM EntityEntity e
        WHERE e.typeId = :entityTypeId
          AND e.workspaceId = :workspaceId
          AND e.sourceExternalId IN :sourceExternalIds
    """)
    fun findByTypeIdAndWorkspaceIdAndSourceExternalIdIn(
        entityTypeId: UUID,
        workspaceId: UUID,
        sourceExternalIds: Collection<String>,
    ): List<EntityEntity>

}
