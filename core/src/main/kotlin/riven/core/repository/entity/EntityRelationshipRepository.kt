package cranium.core.repository.entity

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import cranium.core.entity.entity.EntityRelationshipEntity
import cranium.core.projection.entity.EntityLinkProjection
import java.util.*


/**
 * Repository for relationships between entities.
 */
interface EntityRelationshipRepository : JpaRepository<EntityRelationshipEntity, UUID> {

    /**
     * Find all relationships where the given entity is the source.
     */
    @Query("SELECT r FROM EntityRelationshipEntity r WHERE r.sourceId = :id")
    fun findBySourceId(id: UUID): List<EntityRelationshipEntity>

    @Query("SELECT r FROM EntityRelationshipEntity r WHERE r.sourceId in :ids")
    fun findBySourceIdIn(ids: Collection<UUID>): List<EntityRelationshipEntity>

    @Query("SELECT r FROM EntityRelationshipEntity r WHERE r.targetId in :ids")
    fun findByTargetIdIn(ids: Collection<UUID>): List<EntityRelationshipEntity>

    /**
     * Find all relationships with a specific definitionId where the given entity is the source.
     */
    fun findAllBySourceIdAndDefinitionId(sourceId: UUID, definitionId: UUID): List<EntityRelationshipEntity>

    /**
     * Same as [findAllBySourceIdAndDefinitionId] but acquires a pessimistic write lock
     * to serialize concurrent cardinality enforcement for the same source+definition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM EntityRelationshipEntity r WHERE r.sourceId = :sourceId AND r.definitionId = :definitionId")
    fun findAllBySourceIdAndDefinitionIdForUpdate(sourceId: UUID, definitionId: UUID): List<EntityRelationshipEntity>

    /**
     * Find relationships with a specific source, target, and definitionId.
     */
    fun findBySourceIdAndTargetIdAndDefinitionId(
        sourceId: UUID,
        targetId: UUID,
        definitionId: UUID
    ): List<EntityRelationshipEntity>

    fun countBySourceIdAndDefinitionId(sourceId: UUID, definitionId: UUID): Long

    /**
     * Find all relationships involving the given entity (as source or target).
     */
    @Query(
        """
        SELECT r FROM EntityRelationshipEntity r
        WHERE (r.sourceId = :entityId OR r.targetId = :entityId)
        AND r.workspaceId = :workspaceId
    """
    )
    fun findAllRelationshipsForEntity(entityId: UUID, workspaceId: UUID): List<EntityRelationshipEntity>

    /**
     * Find all relationships for a source entity across multiple definition IDs.
     */
    fun findAllBySourceIdAndDefinitionIdIn(sourceId: UUID, definitionIds: Collection<UUID>): List<EntityRelationshipEntity>

    /**
     * Delete all relationships for a source entity with a specific definition ID.
     */
    fun deleteAllBySourceIdAndDefinitionId(sourceId: UUID, definitionId: UUID)

    /**
     * Delete relationships by source entity and definition ID where target is in the given list.
     */
    fun deleteAllBySourceIdAndDefinitionIdAndTargetIdIn(sourceId: UUID, definitionId: UUID, targetIds: Collection<UUID>)

    /**
     * Hard-delete relationships by source entity, definition ID, and target kind where target
     * is in the given list. Used by the knowledge ingestion path so glossary DEFINES batches
     * targeting different `target_kind` values on the same definition do not delete each
     * other's rows.
     */
    @Modifying
    @Query(
        """
        DELETE FROM EntityRelationshipEntity r
        WHERE r.sourceId = :sourceId
          AND r.definitionId = :definitionId
          AND r.targetKind = :targetKind
          AND r.targetId IN :targetIds
        """,
    )
    fun deleteAllBySourceIdAndDefinitionIdAndTargetKindAndTargetIdIn(
        sourceId: UUID,
        definitionId: UUID,
        targetKind: cranium.core.enums.entity.RelationshipTargetKind,
        targetIds: Collection<UUID>,
    )

    /**
     * Hard-delete relationships scoped to a sub-reference parent (ATTRIBUTE / RELATIONSHIP
     * target kinds). The owning entity_type id is included in the predicate so reconciliation
     * for one parent does not touch rows under a different parent that happen to share
     * (sourceId, definitionId, targetKind).
     */
    @Modifying
    @Query(
        """
        DELETE FROM EntityRelationshipEntity r
        WHERE r.sourceId = :sourceId
          AND r.definitionId = :definitionId
          AND r.targetKind = :targetKind
          AND r.targetParentId = :targetParentId
          AND r.targetId IN :targetIds
        """,
    )
    fun deleteAllBySourceIdAndDefinitionIdAndTargetKindAndTargetParentIdAndTargetIdIn(
        sourceId: UUID,
        definitionId: UUID,
        targetKind: cranium.core.enums.entity.RelationshipTargetKind,
        targetParentId: UUID,
        targetIds: Collection<UUID>,
    )

    /**
     * Hard-delete every relationship row matching `(sourceId, definitionId, targetKind)`
     * regardless of `targetParentId`. Used by the knowledge ingestion path to clear all
     * parent-scoped rows of a given kind when the input carries no refs of that kind, since
     * `replaceForDefinitionInternal` requires a non-null `targetParentId` for ATTRIBUTE / RELATIONSHIP
     * reconciliation.
     */
    @Modifying
    @Query(
        """
        DELETE FROM EntityRelationshipEntity r
        WHERE r.sourceId = :sourceId
          AND r.definitionId = :definitionId
          AND r.targetKind = :targetKind
        """,
    )
    fun deleteAllBySourceIdAndDefinitionIdAndTargetKind(
        sourceId: UUID,
        definitionId: UUID,
        targetKind: cranium.core.enums.entity.RelationshipTargetKind,
    )

    /**
     * Find all relationships for a source entity scoped to a specific definition + target kind.
     * Used by knowledge ingestion to enumerate existing parent-scoped rows so orphan
     * `targetParentId` owners (parents removed from the input) can be reconciled away.
     */
    @Query(
        """
        SELECT r FROM EntityRelationshipEntity r
        WHERE r.sourceId = :sourceId
          AND r.definitionId = :definitionId
          AND r.targetKind = :targetKind
        """,
    )
    fun findAllBySourceIdAndDefinitionIdAndTargetKind(
        sourceId: UUID,
        definitionId: UUID,
        targetKind: cranium.core.enums.entity.RelationshipTargetKind,
    ): List<EntityRelationshipEntity>

    /**
     * Find all relationships for a target entity with a specific definition ID (inverse lookup).
     */
    @Query("""
        SELECT er FROM EntityRelationshipEntity er
        WHERE er.targetId = :targetId
        AND er.definitionId = :definitionId
    """)
    fun findByTargetIdAndDefinitionId(targetId: UUID, definitionId: UUID): List<EntityRelationshipEntity>

    /**
     * Batch variant: find all relationships for multiple target entities with a specific definition ID.
     * Acquires a pessimistic write lock for target-side cardinality enforcement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM EntityRelationshipEntity r WHERE r.targetId IN :targetIds AND r.definitionId = :definitionId")
    fun findByTargetIdInAndDefinitionIdForUpdate(targetIds: Collection<UUID>, definitionId: UUID): List<EntityRelationshipEntity>

    /**
     * Count relationships for a given definition ID.
     */
    fun countByDefinitionId(definitionId: UUID): Long

    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<EntityRelationshipEntity>

    /**
     * Find relationships for a source entity filtered by the system type on the
     * relationship definition. Used by knowledge projectors to enumerate
     * `ATTACHMENT` / `MENTION` / `DEFINES` rows without loading every definition.
     */
    @Query("""
        SELECT r FROM EntityRelationshipEntity r
        JOIN RelationshipDefinitionEntity d ON r.definitionId = d.id
        WHERE r.sourceId = :sourceId
          AND d.systemType = :systemType
    """)
    fun findBySourceIdAndDefinitionSystemType(
        sourceId: UUID,
        systemType: cranium.core.enums.entity.SystemRelationshipType,
    ): List<EntityRelationshipEntity>

    /**
     * Batch variant: find relationships for multiple source entities filtered by
     * definition system type. Used by knowledge projectors when materialising
     * collections (e.g. listing notes for a workspace).
     */
    @Query("""
        SELECT r FROM EntityRelationshipEntity r
        JOIN RelationshipDefinitionEntity d ON r.definitionId = d.id
        WHERE r.sourceId IN :sourceIds
          AND d.systemType = :systemType
    """)
    fun findAllBySourceIdInAndDefinitionSystemType(
        sourceIds: Collection<UUID>,
        systemType: cranium.core.enums.entity.SystemRelationshipType,
    ): List<EntityRelationshipEntity>

    /**
     * Inverse lookup: find relationships pointing AT a target entity, filtered by
     * the system type on the relationship definition. Used to answer
     * "which notes reference this entity".
     */
    @Query("""
        SELECT r FROM EntityRelationshipEntity r
        JOIN RelationshipDefinitionEntity d ON r.definitionId = d.id
        WHERE r.targetId = :targetId
          AND d.systemType = :systemType
    """)
    fun findByTargetIdAndDefinitionSystemType(
        targetId: UUID,
        systemType: cranium.core.enums.entity.SystemRelationshipType,
    ): List<EntityRelationshipEntity>

    /**
     * Count relationship links for a definition where the target entity belongs to a specific entity type.
     */
    @Query("""
        SELECT COUNT(r) FROM EntityRelationshipEntity r
        JOIN EntityEntity e ON r.targetId = e.id
        WHERE r.definitionId = :definitionId AND e.typeId = :targetEntityTypeId AND r.deleted = false
    """)
    fun countByDefinitionIdAndTargetEntityTypeId(definitionId: UUID, targetEntityTypeId: UUID): Long

    /**
     * Soft-delete relationship links for a definition where the target entity belongs to a specific entity type.
     */
    @Modifying
    @Query("""
        UPDATE EntityRelationshipEntity r
        SET r.deleted = true, r.deletedAt = CURRENT_TIMESTAMP
        WHERE r.definitionId = :definitionId
        AND r.deleted = false
        AND r.targetId IN (SELECT e.id FROM EntityEntity e WHERE e.typeId = :targetEntityTypeId)
    """)
    fun softDeleteByDefinitionIdAndTargetEntityTypeId(definitionId: UUID, targetEntityTypeId: UUID)

    /**
     * Find all relationships where the given entity is either source or target for a specific definition.
     */
    @Query("""
        SELECT r FROM EntityRelationshipEntity r
        WHERE (r.sourceId = :entityId OR r.targetId = :entityId)
        AND r.definitionId = :definitionId
        AND r.workspaceId = :workspaceId
    """)
    fun findByEntityIdAndDefinitionId(entityId: UUID, definitionId: UUID, workspaceId: UUID): List<EntityRelationshipEntity>

    /**
     * Soft-delete all relationship links for a given definition ID.
     */
    @Modifying
    @Query("""
        UPDATE EntityRelationshipEntity e
        SET e.deleted = true, e.deletedAt = CURRENT_TIMESTAMP
        WHERE e.definitionId = :definitionId AND e.deleted = false
    """)
    fun softDeleteByDefinitionId(definitionId: UUID)

    @Modifying
    @Query(
        """
        UPDATE entity_relationships
            SET deleted = true,
            deleted_at = CURRENT_TIMESTAMP
        WHERE
            (source_entity_id = ANY(:ids)
            or target_id = ANY(:ids))
            AND workspace_id = :workspaceId
            AND deleted = false
        RETURNING *
            """, nativeQuery = true
    )
    fun deleteEntities(ids: Array<UUID>, workspaceId: UUID): List<EntityRelationshipEntity>

    /**
     * Find all entity links for a source entity by joining relationships with target entities.
     * Extracts the label from the target entity's payload using its identifier_key.
     *
     * The query uses PostgreSQL JSONB operators:
     * - `payload -> identifier_key::text` accesses the JSONB object at the dynamic key
     * - `->> 'value'` extracts the 'value' field as text
     * - COALESCE falls back to the entity ID if the label is not found
     */
    @Query(
        value = """
            SELECT
                e.id as id,
                e.workspace_id as workspaceId,
                r.relationship_definition_id as definitionId,
                r.source_entity_id as sourceEntityId,
                e.icon_type as iconType,
                e.icon_colour as iconColour,
                e.type_key as typeKey,
                COALESCE(ea.value #>> '{}', e.id::text) as label,
                'FORWARD' as direction,
                rd.system_type as systemType
            FROM entity_relationships r
            JOIN entities e ON r.target_id = e.id
            LEFT JOIN entity_attributes ea ON ea.entity_id = e.id AND ea.attribute_id = e.identifier_key AND ea.deleted = false
            JOIN relationship_definitions rd ON r.relationship_definition_id = rd.id AND rd.deleted = false
            WHERE r.source_entity_id = :sourceId
            AND r.deleted = false
            AND e.deleted = false
            AND e.workspace_id = :workspaceId
        """,
        nativeQuery = true
    )
    fun findEntityLinksBySourceId(sourceId: UUID, workspaceId: UUID): List<EntityLinkProjection>

    /**
     * Find all entity links for multiple source entities.
     * Returns a flat list that can be grouped by sourceEntityId in the service layer.
     */
    @Query(
        value = """
            SELECT
                e.id as id,
                e.workspace_id as workspaceId,
                r.relationship_definition_id as definitionId,
                r.source_entity_id as sourceEntityId,
                e.icon_type as iconType,
                e.icon_colour as iconColour,
                e.type_key as typeKey,
                COALESCE(ea.value #>> '{}', e.id::text) as label,
                'FORWARD' as direction,
                rd.system_type as systemType
            FROM entity_relationships r
            JOIN entities e ON r.target_id = e.id
            LEFT JOIN entity_attributes ea ON ea.entity_id = e.id AND ea.attribute_id = e.identifier_key AND ea.deleted = false
            JOIN relationship_definitions rd ON r.relationship_definition_id = rd.id AND rd.deleted = false
            WHERE r.source_entity_id = ANY(:ids)
            AND r.deleted = false
            AND e.deleted = false
            AND e.workspace_id = :workspaceId
        """,
        nativeQuery = true
    )
    fun findEntityLinksBySourceIdIn(ids: Array<UUID>, workspaceId: UUID): List<EntityLinkProjection>

    /**
     * Find inverse entity links where the given entity is a target.
     *
     * The sourceEntityId column aliases r.target_id (the entity being viewed),
     * keeping the EntityLink contract consistent.
     *
     * The predicate admits inverse rows when any of the following hold:
     *  - a relationship_target_rule applies for the target's entity type (the
     *    source's relationship definition explicitly targets that type),
     *  - the definition's `system_type` is `SYSTEM_CONNECTION` (the symmetric
     *    system connection edge), or
     *  - the definition's `system_type` is one of the knowledge edge kinds
     *    (`ATTACHMENT`, `MENTION`, `DEFINES`) AND the source entity's type has
     *    `surface_role = 'KNOWLEDGE'` — i.e. the row is a knowledge entity
     *    referencing this target.
     *
     * `r.target_kind = 'ENTITY'` is enforced so structural DEFINES edges
     * (targeting `ENTITY_TYPE` / `ATTRIBUTE` / `RELATIONSHIP` UUIDs) never
     * leak into entity-instance lookups.
     */
    @Query(
        value = """
            SELECT DISTINCT
                e.id as id,
                e.workspace_id as workspaceId,
                r.relationship_definition_id as definitionId,
                r.target_id as sourceEntityId,
                e.icon_type as iconType,
                e.icon_colour as iconColour,
                e.type_key as typeKey,
                COALESCE(ea.value #>> '{}', e.id::text) as label,
                'INVERSE' as direction,
                rd.system_type as systemType
            FROM entity_relationships r
            JOIN entities e ON r.source_entity_id = e.id
            JOIN entity_types src_t ON src_t.id = e.type_id
            LEFT JOIN entity_attributes ea ON ea.entity_id = e.id AND ea.attribute_id = e.identifier_key AND ea.deleted = false
            JOIN relationship_definitions rd ON r.relationship_definition_id = rd.id AND rd.deleted = false
            JOIN entities target_e ON r.target_id = target_e.id
            LEFT JOIN relationship_target_rules rtr ON rtr.relationship_definition_id = r.relationship_definition_id
            WHERE r.target_id = :targetId
            AND r.target_kind = 'ENTITY'
            AND (
                rtr.target_entity_type_id = target_e.type_id
                OR rd.system_type = 'SYSTEM_CONNECTION'
                OR (
                    rd.system_type IN ('ATTACHMENT', 'MENTION', 'DEFINES')
                    AND src_t.surface_role = 'KNOWLEDGE'
                )
            )
            AND r.deleted = false
            AND e.deleted = false
            AND e.workspace_id = :workspaceId
        """,
        nativeQuery = true
    )
    fun findInverseEntityLinksByTargetId(targetId: UUID, workspaceId: UUID): List<EntityLinkProjection>

    /**
     * Batch variant: find inverse entity links for multiple target entities.
     * Same predicate semantics as [findInverseEntityLinksByTargetId].
     */
    @Query(
        value = """
            SELECT DISTINCT
                e.id as id,
                e.workspace_id as workspaceId,
                r.relationship_definition_id as definitionId,
                r.target_id as sourceEntityId,
                e.icon_type as iconType,
                e.icon_colour as iconColour,
                e.type_key as typeKey,
                COALESCE(ea.value #>> '{}', e.id::text) as label,
                'INVERSE' as direction,
                rd.system_type as systemType
            FROM entity_relationships r
            JOIN entities e ON r.source_entity_id = e.id
            JOIN entity_types src_t ON src_t.id = e.type_id
            LEFT JOIN entity_attributes ea ON ea.entity_id = e.id AND ea.attribute_id = e.identifier_key AND ea.deleted = false
            JOIN relationship_definitions rd ON r.relationship_definition_id = rd.id AND rd.deleted = false
            LEFT JOIN relationship_target_rules rtr ON rtr.relationship_definition_id = r.relationship_definition_id
            JOIN entities target_e ON r.target_id = target_e.id
            WHERE r.target_id = ANY(:ids)
            AND r.target_kind = 'ENTITY'
            AND (
                rtr.target_entity_type_id = target_e.type_id
                OR rd.system_type = 'SYSTEM_CONNECTION'
                OR (
                    rd.system_type IN ('ATTACHMENT', 'MENTION', 'DEFINES')
                    AND src_t.surface_role = 'KNOWLEDGE'
                )
            )
            AND r.deleted = false
            AND e.deleted = false
            AND e.workspace_id = :workspaceId
        """,
        nativeQuery = true
    )
    fun findInverseEntityLinksByTargetIdIn(ids: Array<UUID>, workspaceId: UUID): List<EntityLinkProjection>
}
