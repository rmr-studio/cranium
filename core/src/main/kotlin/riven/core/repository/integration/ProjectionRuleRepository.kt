package cranium.core.repository.integration

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import cranium.core.entity.integration.ProjectionRuleEntity
import java.util.*

/**
 * Repository for entity type projection rules — maps integration entity types to core lifecycle types.
 */
interface ProjectionRuleRepository : JpaRepository<ProjectionRuleEntity, UUID> {

    /** Find all projection rules where the given entity type is the source. */
    @Query("SELECT r FROM ProjectionRuleEntity r WHERE r.sourceEntityTypeId = :sourceEntityTypeId")
    fun findBySourceEntityTypeId(sourceEntityTypeId: UUID): List<ProjectionRuleEntity>

    /** Find all projection rules for a workspace (system rules + workspace-specific rules). */
    @Query(
        "SELECT r FROM ProjectionRuleEntity r WHERE r.sourceEntityTypeId = :sourceEntityTypeId " +
            "AND (r.workspaceId IS NULL OR r.workspaceId = :workspaceId)"
    )
    fun findBySourceEntityTypeIdAndWorkspace(sourceEntityTypeId: UUID, workspaceId: UUID): List<ProjectionRuleEntity>

    /** Check if a rule already exists for this source→target pair in a workspace. */
    @Query(
        "SELECT COUNT(r) > 0 FROM ProjectionRuleEntity r " +
            "WHERE r.workspaceId = :workspaceId AND r.sourceEntityTypeId = :sourceEntityTypeId " +
            "AND r.targetEntityTypeId = :targetEntityTypeId"
    )
    fun existsByWorkspaceAndSourceAndTarget(
        workspaceId: UUID,
        sourceEntityTypeId: UUID,
        targetEntityTypeId: UUID
    ): Boolean
}
