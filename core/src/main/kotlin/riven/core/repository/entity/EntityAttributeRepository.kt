package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import riven.core.entity.entity.EntityAttributeEntity
import java.util.*

/**
 * Repository for normalized entity attribute values.
 */
interface EntityAttributeRepository : JpaRepository<EntityAttributeEntity, UUID> {

    /**
     * Find all attributes for a single entity.
     */
    fun findByEntityId(entityId: UUID): List<EntityAttributeEntity>

    /**
     * Find all attributes for multiple entities in a single query.
     */
    @Query("SELECT a FROM EntityAttributeEntity a WHERE a.entityId IN :entityIds")
    fun findByEntityIdIn(entityIds: Collection<UUID>): List<EntityAttributeEntity>

    /**
     * Hard-delete all attributes for a given entity.
     * Used in the delete-all + re-insert save flow.
     */
    @Modifying
    @Query(
        value = "DELETE FROM entity_attributes WHERE entity_id = :entityId",
        nativeQuery = true
    )
    fun hardDeleteByEntityId(entityId: UUID)

    /**
     * Soft-delete all attributes for the given entity IDs within a workspace.
     */
    @Modifying
    @Query(
        value = """
            UPDATE entity_attributes
            SET deleted = true, deleted_at = CURRENT_TIMESTAMP
            WHERE entity_id = ANY(:entityIds)
            AND workspace_id = :workspaceId
            AND deleted = false
        """,
        nativeQuery = true
    )
    fun softDeleteByEntityIds(entityIds: Array<UUID>, workspaceId: UUID)
}
