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

    /**
     * Batch identifier value lookup for projection identity resolution.
     * Finds attributes where the attribute_id is one of the IDENTIFIER attributes
     * AND the text value matches one of the candidate values, scoped to a specific entity type.
     *
     * Used by [riven.core.service.ingestion.IdentityResolutionService] for Check 2.
     */
    @Query(
        value = """
            SELECT ea.* FROM entity_attributes ea
            JOIN entities e ON ea.entity_id = e.id AND e.deleted = false
            WHERE ea.workspace_id = :workspaceId
              AND e.type_id = :entityTypeId
              AND ea.attribute_id IN (:attributeIds)
              AND ea.value ->> 'value' IN (:textValues)
              AND ea.deleted = false
        """,
        nativeQuery = true
    )
    fun findByIdentifierValuesForEntityType(
        workspaceId: UUID,
        entityTypeId: UUID,
        attributeIds: Collection<UUID>,
        textValues: Collection<String>,
    ): List<EntityAttributeEntity>

    /**
     * Count entity attributes for a given entity type and attribute definition.
     * Used by schema reconciliation to assess impact of breaking changes.
     *
     * Soft-delete filtering is applied automatically by @SQLRestriction on the entity.
     */
    @Query(
        "SELECT COUNT(ea) FROM EntityAttributeEntity ea " +
            "WHERE ea.typeId = :typeId AND ea.attributeId = :attributeId"
    )
    fun countByTypeIdAndAttributeId(typeId: UUID, attributeId: UUID): Long

    /**
     * Hard-delete all attributes for a given entity type and attribute definition.
     * Used by schema reconciliation when applying FIELD_REMOVED breaking changes.
     *
     * JPQL bulk DELETE intentionally bypasses @SQLRestriction soft-delete handling — FIELD_REMOVED
     * is a destructive reconcile and the rows must be removed, not marked deleted.
     */
    @Modifying
    @Query(
        "DELETE FROM EntityAttributeEntity ea " +
            "WHERE ea.typeId = :typeId AND ea.attributeId = :attributeId"
    )
    fun deleteAllByTypeIdAndAttributeId(typeId: UUID, attributeId: UUID)

    /**
     * Find entity attributes whose JSONB value column contains a text value matching the given string.
     *
     * Uses the ->> operator to extract the JSON text as a plain string for comparison.
     * Only matches attributes for the given workspace, attribute definition, and where the
     * JSONB text representation equals the supplied value exactly.
     *
     * Used by [riven.core.service.identity.IdentityLookupService] for identifier-based entity lookup.
     */
    @Query(
        value = """
            SELECT ea.* FROM entity_attributes ea
            WHERE ea.workspace_id = :workspaceId
              AND ea.attribute_id = :attributeId
              AND ea.value ->> 'value' = :textValue
              AND ea.deleted = false
        """,
        nativeQuery = true
    )
    fun findByWorkspaceIdAndAttributeIdAndTextValue(
        workspaceId: UUID,
        attributeId: UUID,
        textValue: String,
    ): List<EntityAttributeEntity>
}
