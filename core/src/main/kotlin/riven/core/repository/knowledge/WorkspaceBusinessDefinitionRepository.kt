package riven.core.repository.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import riven.core.entity.knowledge.WorkspaceBusinessDefinitionEntity
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionStatus
import java.util.*

interface WorkspaceBusinessDefinitionRepository : JpaRepository<WorkspaceBusinessDefinitionEntity, UUID> {

    @Query(
        """
        SELECT d FROM WorkspaceBusinessDefinitionEntity d
        WHERE d.workspaceId = :workspaceId
        AND (:status IS NULL OR d.status = :status)
        AND (:category IS NULL OR d.category = :category)
        """
    )
    fun findByWorkspaceIdWithFilters(
        workspaceId: UUID,
        status: DefinitionStatus?,
        category: DefinitionCategory?,
    ): List<WorkspaceBusinessDefinitionEntity>

    @Query("SELECT d FROM WorkspaceBusinessDefinitionEntity d WHERE d.id = :id AND d.workspaceId = :workspaceId")
    fun findByIdAndWorkspaceId(id: UUID, workspaceId: UUID): Optional<WorkspaceBusinessDefinitionEntity>

    @Query(
        """
        SELECT d FROM WorkspaceBusinessDefinitionEntity d
        WHERE d.workspaceId = :workspaceId AND d.normalizedTerm = :normalizedTerm
        """
    )
    fun findByWorkspaceIdAndNormalizedTerm(
        workspaceId: UUID,
        normalizedTerm: String,
    ): Optional<WorkspaceBusinessDefinitionEntity>
}
