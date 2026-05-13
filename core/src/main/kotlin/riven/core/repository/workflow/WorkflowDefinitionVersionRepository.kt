package cranium.core.repository.workflow

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import cranium.core.entity.workflow.WorkflowDefinitionVersionEntity
import java.util.*

/**
 * Repository for accessing WorkflowDefinitionVersionEntity instances.
 *
 * Workflow versions contain the actual DAG structure (nodes and edges).
 */
@Repository
interface WorkflowDefinitionVersionRepository : JpaRepository<WorkflowDefinitionVersionEntity, UUID> {

    @Query(
        """
        SELECT v FROM WorkflowDefinitionVersionEntity v
        WHERE v.workflowDefinitionId = :workflowDefinitionId
        AND v.versionNumber = :versionNumber
        """
    )
    fun findByWorkflowDefinitionIdAndVersionNumber(
        workflowDefinitionId: UUID,
        versionNumber: Int
    ): WorkflowDefinitionVersionEntity?
}
