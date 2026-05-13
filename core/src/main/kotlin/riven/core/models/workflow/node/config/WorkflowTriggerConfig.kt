package cranium.core.models.workflow.node.config

import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.enums.workflow.WorkflowTriggerType

/**
 * Configuration interface for TRIGGER category nodes.
 *
 * Trigger nodes are entry points that start workflow execution.
 * They don't execute during the workflow - they define when/how
 * the workflow should be initiated.
 *
 * @property subType The specific trigger type (SCHEDULE, ENTITY_EVENT, WEBHOOK, etc.)
 */
interface WorkflowTriggerConfig : WorkflowNodeConfig {
    override val type: WorkflowNodeType
        get() = WorkflowNodeType.TRIGGER
    val subType: WorkflowTriggerType
}