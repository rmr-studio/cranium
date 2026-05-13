package cranium.core.models.response.workflow

import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeOutputMetadata
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata

/**
 * Complete schema and metadata response for a single workflow node type.
 *
 * Contains all information needed by the frontend to render node type
 * selectors, configuration forms, and documentation.
 */
@Schema(
    description = "Complete schema and metadata for a workflow node type"
)
data class NodeTypeSchemaResponse(
    @Schema(
        description = "Node type category (ACTION, TRIGGER, CONTROL_FLOW, FUNCTION)",
        example = "ACTION"
    )
    val type: WorkflowNodeType,

    @Schema(
        description = "Specific node subtype within the category",
        example = "CREATE_ENTITY"
    )
    val subType: String,

    @Schema(
        description = "Display metadata (label, description, icon, category)"
    )
    val metadata: WorkflowNodeTypeMetadata,

    @Schema(
        description = "Configuration field definitions for this node type"
    )
    val configSchema: List<WorkflowNodeConfigField>,

    @Schema(
        description = "Output field definitions describing the shape of this node's execution output"
    )
    val outputMetadata: WorkflowNodeOutputMetadata? = null
)
