package riven.core.repository.entity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.entity.RelationshipDefinitionExclusionEntity
import java.util.*

@Repository
interface RelationshipDefinitionExclusionRepository : JpaRepository<RelationshipDefinitionExclusionEntity, UUID> {

    fun findByEntityTypeId(entityTypeId: UUID): List<RelationshipDefinitionExclusionEntity>

    fun findByEntityTypeIdIn(entityTypeIds: List<UUID>): List<RelationshipDefinitionExclusionEntity>

    fun findByRelationshipDefinitionId(definitionId: UUID): List<RelationshipDefinitionExclusionEntity>

    fun findByRelationshipDefinitionIdIn(definitionIds: List<UUID>): List<RelationshipDefinitionExclusionEntity>

    fun findByRelationshipDefinitionIdAndEntityTypeId(
        definitionId: UUID,
        entityTypeId: UUID,
    ): Optional<RelationshipDefinitionExclusionEntity>

    @Transactional
    fun deleteByRelationshipDefinitionIdAndEntityTypeId(definitionId: UUID, entityTypeId: UUID)

    @Transactional
    fun deleteByRelationshipDefinitionId(definitionId: UUID)
}
