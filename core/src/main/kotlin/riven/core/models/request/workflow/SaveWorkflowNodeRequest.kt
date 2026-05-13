package cranium.core.models.request.workflow

import cranium.core.models.workflow.node.config.WorkflowNodeConfig
import java.util.*

/**
 * Request model for saving a workflow node (create or update).
 *
 * If [id] is null, a new node is created.
 * If [id] is provided, the existing node is updated.
 *
 * For updates:
 * - Metadata updates (name, description) are applied in place
 * - Config updates trigger creation of a new version (immutable pattern)
 *
 * @property id Optional node ID - if present, performs update; if null, creates new node
 * @property key Unique identifier for the node within the workspace (required for create)
 * @property name Human-readable display name
 * @property description Optional description of the node's purpose
 * @property config Polymorphic node configuration (ActionConfig, ControlConfig, etc.)
 */
data class SaveWorkflowNodeRequest(
    val id: UUID? = null,
    val key: String? = null,
    val name: String,
    val description: String? = null,
    val config: WorkflowNodeConfig,
)
