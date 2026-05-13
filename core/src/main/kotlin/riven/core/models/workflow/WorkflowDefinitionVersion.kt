package cranium.core.models.workflow

import cranium.core.entity.util.AuditableModel
import java.time.ZonedDateTime
import java.util.*

data class WorkflowDefinitionVersion(
    val id: UUID,
    val version: Int,
    // Workflow traversal graph -> Should be populated for singleton queries, null for list queries
    val workflow: WorkflowGraph? = null,
    // Visual representation of the workflow for UI
    val canvas: Any,

    override var createdAt: ZonedDateTime? = null,
    override var updatedAt: ZonedDateTime? = null,
    override var createdBy: UUID? = null,
    override var updatedBy: UUID? = null,
) : AuditableModel