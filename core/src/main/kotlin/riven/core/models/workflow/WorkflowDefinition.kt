package cranium.core.models.workflow

import cranium.core.entity.util.AuditableModel
import cranium.core.enums.workflow.WorkflowDefinitionStatus
import cranium.core.models.common.Icon
import java.time.ZonedDateTime
import java.util.*

data class WorkflowDefinition(
    val id: UUID,
    val workspaceId: UUID,

    val name: String,
    val description: String? = null,
    val status: WorkflowDefinitionStatus,
    val icon: Icon,
    val tags: List<String>,

    // Link to the current active version that defines the workflow
    val definition: WorkflowDefinitionVersion,

    override var createdAt: ZonedDateTime? = null,
    override var updatedAt: ZonedDateTime? = null,
    override var createdBy: UUID? = null,
    override var updatedBy: UUID? = null

) : AuditableModel