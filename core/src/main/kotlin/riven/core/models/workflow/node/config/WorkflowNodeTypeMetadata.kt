package cranium.core.models.workflow.node.config

import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowNodeType

/**
 * Metadata for workflow node types, providing display information for UI rendering.
 *
 * This metadata is defined in each config's companion object and exposed via the
 * `/api/v1/workflow/definitions/node-schemas` endpoint.
 *
 * @property label Human-readable name for the node type (e.g., "Create Entity")
 * @property description Brief explanation of what the node does
 * @property icon Icon type from the IconType enum for UI display
 * @property category UI category for grouping nodes (e.g., "trigger", "action", "condition")
 */
@Schema(
    description = "Display metadata for a workflow node type"
)
data class WorkflowNodeTypeMetadata(
    @param:Schema(
        description = "Human-readable label for the node type",
        example = "Create Entity"
    )
    val label: String,

    @param:Schema(
        description = "Brief description of what the node does",
        example = "Creates a new entity instance"
    )
    val description: String,

    @param:Schema(
        description = "Icon type for UI rendering"
    )
    val icon: IconType,

    @param:Schema(
        description = "UI category for grouping (trigger, action, condition, utility)",
        example = "action"
    )
    val category: WorkflowNodeType
)
