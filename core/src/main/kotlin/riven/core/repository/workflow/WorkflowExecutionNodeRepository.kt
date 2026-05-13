package cranium.core.repository.workflow

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import cranium.core.entity.workflow.execution.WorkflowExecutionNodeEntity
import java.util.UUID

/**
 * Repository for accessing WorkflowExecutionNodeEntity instances.
 *
 * Provides standard CRUD operations and custom queries for workflow node execution records.
 */
@Repository
interface WorkflowExecutionNodeRepository : JpaRepository<WorkflowExecutionNodeEntity, UUID> {

    /**
     * Find all node execution records for a workflow execution.
     *
     * @param workflowExecutionId Workflow execution ID
     * @return List of node execution records ordered by sequence
     */
    fun findByWorkflowExecutionIdOrderBySequenceIndexAsc(workflowExecutionId: UUID): List<WorkflowExecutionNodeEntity>

    /**
     * Find a specific node execution by workflow execution and node ID.
     *
     * @param workflowExecutionId Workflow execution ID
     * @param nodeId Node ID
     * @return The node execution record if found
     */
    fun findByWorkflowExecutionIdAndNodeId(workflowExecutionId: UUID, nodeId: UUID): WorkflowExecutionNodeEntity?
}
