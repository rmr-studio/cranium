package cranium.core.models.workflow.node.config

import cranium.core.enums.workflow.WorkflowControlType
import cranium.core.enums.workflow.WorkflowNodeType

/**
 * Configuration interface for CONTROL_FLOW category nodes.
 *
 * Control flow nodes manage workflow execution branching and looping,
 * such as conditions, switches, and loops.
 *
 * @property subType The specific control type (CONDITION, SWITCH, LOOP, etc.)
 */
interface WorkflowControlConfig : WorkflowNodeConfig {
    override val type: WorkflowNodeType
        get() = WorkflowNodeType.CONTROL_FLOW
    val subType: WorkflowControlType
}