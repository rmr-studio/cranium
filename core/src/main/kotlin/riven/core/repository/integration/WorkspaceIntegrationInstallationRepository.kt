package riven.core.repository.integration

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import riven.core.entity.integration.WorkspaceIntegrationInstallationEntity
import java.util.*

/**
 * Repository for workspace integration installations.
 *
 * Active (non-deleted) installations are returned by all derived queries due to
 * the @SQLRestriction on the entity. Use [findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId]
 * to reach soft-deleted rows for re-enable scenarios.
 */
interface WorkspaceIntegrationInstallationRepository :
    JpaRepository<WorkspaceIntegrationInstallationEntity, UUID> {

    /**
     * Find an active installation for a specific workspace and integration definition.
     */
    fun findByWorkspaceIdAndIntegrationDefinitionId(
        workspaceId: UUID,
        integrationDefinitionId: UUID
    ): WorkspaceIntegrationInstallationEntity?

    /**
     * Find all active installations for a workspace.
     */
    fun findByWorkspaceId(workspaceId: UUID): List<WorkspaceIntegrationInstallationEntity>

    /**
     * Finds a soft-deleted installation record for re-enable scenarios.
     * Bypasses @SQLRestriction to find deleted records.
     */
    @Query(
        value = """
            SELECT * FROM workspace_integration_installations
            WHERE workspace_id = :workspaceId
              AND integration_definition_id = :integrationDefinitionId
              AND deleted = true
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findSoftDeletedByWorkspaceIdAndIntegrationDefinitionId(
        @Param("workspaceId") workspaceId: UUID,
        @Param("integrationDefinitionId") integrationDefinitionId: UUID
    ): WorkspaceIntegrationInstallationEntity?
}
