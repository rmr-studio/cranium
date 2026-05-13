package cranium.core.models.workflow.node.config

import cranium.core.enums.workflow.WorkflowActionType
import cranium.core.enums.workflow.WorkflowNodeType

/**
 * Configuration interface for ACTION category nodes.
 *
 * Action nodes perform business operations like creating entities,
 * making HTTP requests, updating data, etc.
 *
 * @property subType The specific action type (CREATE_ENTITY, HTTP_REQUEST, etc.)
 */
interface WorkflowActionConfig : WorkflowNodeConfig {
    override val type: WorkflowNodeType
        get() = WorkflowNodeType.ACTION
    val subType: WorkflowActionType
}