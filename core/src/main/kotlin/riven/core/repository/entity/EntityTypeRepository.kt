package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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

    @Query("SELECT e FROM EntityTypeEntity e WHERE e.sourceIntegrationId = :integrationId AND e.workspaceId = :workspaceId")
    fun findBySourceIntegrationIdAndWorkspaceId(
        @Param("integrationId") integrationId: UUID,
        @Param("workspaceId") workspaceId: UUID
    ): List<EntityTypeEntity>

    @Query(
        value = """
            SELECT * FROM entity_types
            WHERE source_integration_id = :integrationId
              AND workspace_id = :workspaceId
              AND deleted = true
        """,
        nativeQuery = true
    )
    fun findSoftDeletedBySourceIntegrationIdAndWorkspaceId(
        @Param("integrationId") integrationId: UUID,
        @Param("workspaceId") workspaceId: UUID
    ): List<EntityTypeEntity>

    /**
     * Promotes existing entity types to template status by setting protected=true,
     * sourceType=TEMPLATE, and the specified lifecycle domain.
     * Used when the template installation encounters entity types that already exist
     * in the workspace (e.g. from a previously installed template).
     */
    @Modifying
    @Query(
        value = """
            UPDATE entity_types
            SET protected = true,
                source_type = 'TEMPLATE',
                lifecycle_domain = :lifecycleDomain,
                source_manifest_id = :sourceManifestId,
                source_schema_hash = :sourceSchemaHash,
                updated_at = now()
            WHERE id = :entityTypeId
              AND workspace_id = :workspaceId
        """,
        nativeQuery = true
    )
    fun promoteToTemplate(
        @Param("entityTypeId") entityTypeId: UUID,
        @Param("workspaceId") workspaceId: UUID,
        @Param("lifecycleDomain") lifecycleDomain: String,
        @Param("sourceManifestId") sourceManifestId: UUID,
        @Param("sourceSchemaHash") sourceSchemaHash: String?,
    ): Int

}
