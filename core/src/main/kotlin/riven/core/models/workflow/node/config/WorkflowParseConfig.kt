package cranium.core.models.workflow.node.config

import cranium.core.enums.workflow.WorkflowNodeType

/**
 * Configuration interface for PARSE category nodes.
 *
 * Parse nodes are responsible for interpreting and extracting
 * data from various input formats (e.g., JSON, XML, CSV).
 * and transforming it into specified outputs and data structures via
 * language processing models and AI models.
 *
 * (Ie. Parsing an email body to extract key information to create specific entity types)
 *
 * This should act as a middle ground betweeen two nodes A -> [B] -> C
 * and should take in
 *  - The ability to retrieve output data from A
 *  - The registry requriements from C (what data needs to be accessed/made)
 *  - The parsing logic to transform A's output into C's required input (Natural language prompting/descriptions)
 */
interface WorkflowParseConfig : WorkflowNodeConfig {
    override val type: WorkflowNodeType
        get() = WorkflowNodeType.PARSE
}