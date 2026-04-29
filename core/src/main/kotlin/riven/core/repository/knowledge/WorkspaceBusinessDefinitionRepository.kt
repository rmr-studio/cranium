package riven.core.repository.knowledge

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.knowledge.WorkspaceBusinessDefinitionEntity
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionStatus
import java.time.ZonedDateTime
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

    /**
     * Paginated keyset walk over `(createdAt DESC, id DESC)` for the glossary backfill
     * workflow. Returns rows strictly older than the supplied cursor; the workflow seeds
     * the first page with a far-future cursor.
     */
    @Query(
        """
        SELECT d FROM WorkspaceBusinessDefinitionEntity d
        WHERE d.workspaceId = :workspaceId
        AND (d.createdAt < :cursorCreatedAt OR (d.createdAt = :cursorCreatedAt AND d.id < :cursorId))
        ORDER BY d.createdAt DESC, d.id DESC
        """
    )
    fun findByWorkspaceIdPaged(
        @Param("workspaceId") workspaceId: UUID,
        @Param("cursorCreatedAt") cursorCreatedAt: ZonedDateTime,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable,
    ): List<WorkspaceBusinessDefinitionEntity>
}
