package cranium.core.repository.workflow.projection

import cranium.core.entity.workflow.WorkflowDefinitionEntity
import cranium.core.entity.workflow.WorkflowDefinitionVersionEntity

/**
 * Projection for workflow definition with its published version.
 * Fetched in a single JOIN query to avoid N+1 queries.
 */
data class WorkflowDefinitionWithVersionProjection(
    val definition: WorkflowDefinitionEntity,
    val version: WorkflowDefinitionVersionEntity
)
