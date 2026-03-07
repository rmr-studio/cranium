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

    @Query("""
        SELECT rd, rtr FROM RelationshipDefinitionEntity rd
        LEFT JOIN RelationshipTargetRuleEntity rtr ON rtr.relationshipDefinitionId = rd.id
        WHERE (rd.workspaceId = :workspaceId AND rd.sourceEntityTypeId IN :entityTypeIds)
        OR rd.id IN (
            SELECT rtr2.relationshipDefinitionId FROM RelationshipTargetRuleEntity rtr2
            WHERE rtr2.targetEntityTypeId IN :entityTypeIds
        )
    """)
    fun findDefinitionsWithRulesForEntityTypes(workspaceId: UUID, entityTypeIds: List<UUID>): List<Array<Any?>>
}
