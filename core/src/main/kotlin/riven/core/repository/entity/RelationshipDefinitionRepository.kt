package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.entity.SystemRelationshipType
import java.util.*

@Repository
interface RelationshipDefinitionRepository : JpaRepository<RelationshipDefinitionEntity, UUID> {

    fun findByWorkspaceIdAndSourceEntityTypeId(workspaceId: UUID, sourceEntityTypeId: UUID): List<RelationshipDefinitionEntity>

    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<RelationshipDefinitionEntity>

    fun findBySourceEntityTypeIdAndSystemType(
        sourceEntityTypeId: UUID,
        systemType: SystemRelationshipType,
    ): Optional<RelationshipDefinitionEntity>

    @Query("""
        SELECT rd FROM RelationshipDefinitionEntity rd
        WHERE rd.workspaceId = :workspaceId
        AND rd.sourceEntityTypeId IN :entityTypeIds
    """)
    fun findByWorkspaceIdAndSourceEntityTypeIdIn(workspaceId: UUID, entityTypeIds: List<UUID>): List<RelationshipDefinitionEntity>

    fun findByWorkspaceIdAndSourceEntityTypeIdAndName(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        name: String
    ): Optional<RelationshipDefinitionEntity>

    @Query(
        "SELECT * FROM relationship_definitions WHERE workspace_id = :workspaceId AND source_entity_type_id = :sourceEntityTypeId AND name = :name AND deleted = true",
        nativeQuery = true
    )
    fun findSoftDeletedByWorkspaceIdAndSourceEntityTypeIdAndName(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        name: String
    ): RelationshipDefinitionEntity?

    @Query("""
        SELECT rd, rtr FROM RelationshipDefinitionEntity rd
        LEFT JOIN RelationshipTargetRuleEntity rtr ON rtr.relationshipDefinitionId = rd.id
        WHERE rd.workspaceId = :workspaceId
        AND (
            rd.sourceEntityTypeId IN :entityTypeIds
            OR rd.id IN (
                SELECT rtr2.relationshipDefinitionId FROM RelationshipTargetRuleEntity rtr2
                WHERE rtr2.targetEntityTypeId IN :entityTypeIds
            )
            OR rd.id IN (
                SELECT rtr3.relationshipDefinitionId FROM RelationshipTargetRuleEntity rtr3, EntityTypeEntity et
                WHERE rtr3.semanticTypeConstraint IS NOT NULL
                AND rtr3.semanticTypeConstraint = et.semanticGroup
                AND et.id IN :entityTypeIds
                AND et.semanticGroup <> riven.core.enums.entity.semantics.SemanticGroup.UNCATEGORIZED
            )
        )
    """)
    fun findDefinitionsWithRulesForEntityTypes(workspaceId: UUID, entityTypeIds: List<UUID>): List<Array<Any?>>
}
