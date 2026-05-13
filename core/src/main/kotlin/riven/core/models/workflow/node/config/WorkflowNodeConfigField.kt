package cranium.core.models.workflow.node.config

import cranium.core.enums.workflow.WorkflowNodeConfigFieldType

data class WorkflowNodeConfigField(
    val key: String,
    val label: String,
    val type: WorkflowNodeConfigFieldType,
    val required: Boolean = false,
    val description: String? = null,
    val placeholder: String? = null,
    val defaultValue: Any? = null,
    val validation: Map<String, Any>? = null,
    val options: Map<String, String>? = null
)

