package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import riven.core.entity.entity.EntityUniqueValueEntity
import java.util.*

interface EntityUniqueValuesRepository : JpaRepository<EntityUniqueValueEntity, UUID> {

    /**
     * Check if a conflict exists for a unique value, excluding the current entity.
     * Uses native query to completely avoid Hibernate entity tracking.
     */
    @Query(
        value = """
            SELECT EXISTS(
                SELECT 1 FROM entities_unique_values
                WHERE type_id = :typeId
                  AND field_id = :fieldId
                  AND field_value = :fieldValue
                  AND deleted = false
                  AND (:excludeEntityId IS NULL OR entity_id <> :excludeEntityId)
            )
        """,
        nativeQuery = true
    )
    fun existsConflict(
        typeId: UUID,
        fieldId: UUID,
        fieldValue: String,
        excludeEntityId: UUID? = null
    ): Boolean

    /**
     * Delete all unique values for an entity using native SQL.
     * Completely bypasses Hibernate entity tracking.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = "DELETE FROM entities_unique_values WHERE entity_id = :entityId",
        nativeQuery = true
    )
    fun deleteAllByEntityId(entityId: UUID): Int

    /**
     * Insert a unique value using native SQL.
     * Bypasses Hibernate to avoid stale state issues.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO entities_unique_values (id, workspace_id, type_id, entity_id, field_id, field_value, deleted)
            VALUES (:id, :workspaceId, :typeId, :entityId, :fieldId, :fieldValue, false)
        """,
        nativeQuery = true
    )
    fun insertUniqueValue(
        id: UUID,
        workspaceId: UUID,
        typeId: UUID,
        entityId: UUID,
        fieldId: UUID,
        fieldValue: String
    )

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE entities_unique_values
            SET deleted = true, deleted_at = CURRENT_TIMESTAMP
            WHERE
                workspace_id = :workspaceId
                AND entity_id in :ids
                AND deleted = false
        """,
        nativeQuery = true
    )
    fun deleteEntities(workspaceId: UUID, ids: Collection<UUID>): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE entities_unique_values
            SET deleted = true, deleted_at = CURRENT_TIMESTAMP
            WHERE 
                workspace_id = :workspaceId
                AND type_id = :typeId 
                AND deleted = false
        """,
        nativeQuery = true
    )
    fun deleteType(workspaceId: UUID, typeId: UUID): Int
}
