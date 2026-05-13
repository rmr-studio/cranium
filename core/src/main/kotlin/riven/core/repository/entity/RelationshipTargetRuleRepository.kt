package cranium.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.entity.RelationshipTargetRuleEntity
import java.util.*

@Repository
interface RelationshipTargetRuleRepository : JpaRepository<RelationshipTargetRuleEntity, UUID> {

    fun findByRelationshipDefinitionId(definitionId: UUID): List<RelationshipTargetRuleEntity>

    fun findByRelationshipDefinitionIdIn(definitionIds: List<UUID>): List<RelationshipTargetRuleEntity>

    @Transactional
    fun deleteByRelationshipDefinitionId(definitionId: UUID)

    @Transactional
    fun deleteByTargetEntityTypeId(entityTypeId: UUID)

    fun findByTargetEntityTypeId(entityTypeId: UUID): List<RelationshipTargetRuleEntity>

    fun findByTargetEntityTypeIdIn(entityTypeIds: List<UUID>): List<RelationshipTargetRuleEntity>
}
