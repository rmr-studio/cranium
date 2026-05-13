package cranium.core.repository.workflow

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import cranium.core.entity.workflow.WorkflowDefinitionEntity
import cranium.core.repository.workflow.projection.WorkflowDefinitionWithVersionProjection
import java.util.*

/**
 * Repository for accessing WorkflowDefinitionEntity instances.
 *
 * Provides standard CRUD operations and custom queries for workflow definitions.
 */
@Repository
interface WorkflowDefinitionRepository : JpaRepository<WorkflowDefinitionEntity, UUID> {

    /**
     * Find workflow definitions by workspace.
     *
     * @param workspaceId Workspace context
     * @return List of workflow definitions in the workspace
     */
    @Query(
        """
        SELECT d
        FROM WorkflowDefinitionEntity d
        WHERE d.workspaceId = :workspaceId
        """
    )
    fun findByWorkspaceId(workspaceId: UUID): List<WorkflowDefinitionEntity>


    /**
     * Find a workflow definition with its published version in a single JOIN query.
     *
     * @param definitionId Workflow definition ID
     * @return Projection containing both the definition and its published version, or null if not found
     */
    @Query(
        """
        SELECT new cranium.core.repository.workflow.projection.WorkflowDefinitionWithVersionProjection(d, v)
        FROM WorkflowDefinitionEntity d
        JOIN WorkflowDefinitionVersionEntity v
            ON v.workflowDefinitionId = d.id
            AND v.versionNumber = d.versionNumber
        WHERE d.id = :definitionId
        """
    )
    fun findDefinitionWithPublishedVersion(definitionId: UUID): WorkflowDefinitionWithVersionProjection?
}
