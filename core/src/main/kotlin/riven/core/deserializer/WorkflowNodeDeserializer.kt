package riven.core.deserializer

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import riven.core.enums.workflow.*
import riven.core.models.workflow.node.config.*
import riven.core.models.workflow.node.config.actions.*
import riven.core.models.workflow.node.config.trigger.WorkflowEntityEventTriggerConfig
import riven.core.models.workflow.node.config.trigger.WorkflowFunctionTriggerConfig
import riven.core.models.workflow.node.config.trigger.WorkflowScheduleTriggerConfig
import riven.core.models.workflow.node.config.trigger.WorkflowWebhookTriggerConfig
import riven.core.models.workflow.node.config.controls.WorkflowConditionControlConfig
import riven.core.util.getEnumFromField

/**
 * Jackson deserializer for [WorkflowNodeConfig].
 *
 * Handles two-level discriminator:
 * 1. First level: `type` field (WorkflowNodeType) - determines node category
 * 2. Second level: `subType` field (WorkflowTriggerType, WorkflowActionType, etc.) - determines concrete class
 *
 * Example JSON:
 * ```json
 * {
 *   "version": 1,
 *   "type": "TRIGGER",
 *   "subType": "SCHEDULE",
 *   "cronExpression": "0 0 * * *",
 *   "timeZone": "UTC"
 * }
 * ```
 *
 * Note: Unlike the previous WorkflowNode, WorkflowNodeConfig does NOT have an `id` field.
 * The ID is managed by WorkflowNodeEntity and combined with config in ExecutableNode.
 *
 * Based on the proven Block system pattern (see [MetadataDeserializer]).
 */
class WorkflowNodeConfigDeserializer : ValueDeserializer<WorkflowNodeConfig>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): WorkflowNodeConfig {
        val node = ctxt.readTree(p) as JsonNode

        // Level 1: Extract main category
        val nodeType = ctxt.getEnumFromField<WorkflowNodeType>(
            node,
            "type",
            WorkflowNodeConfig::class.java
        )

        // Level 2: Route to category-specific handler
        return when (nodeType) {
            WorkflowNodeType.TRIGGER -> deserializeTriggerConfig(p, ctxt, node)
            WorkflowNodeType.ACTION -> deserializeActionConfig(p, ctxt, node)
            WorkflowNodeType.CONTROL_FLOW -> deserializeControlConfig(p, ctxt, node)
            WorkflowNodeType.UTILITY -> deserializeUtilityConfig(p, ctxt, node)
            WorkflowNodeType.FUNCTION -> deserializeFunctionConfig(p, ctxt, node)
            WorkflowNodeType.PARSE -> deserializeParseConfig(p, ctxt, node)
        }
    }

    /**
     * Deserializes PARSE category configs.
     * TODO: Implement concrete parse config classes.
     */
    private fun deserializeParseConfig(
        p: JsonParser,
        ctxt: DeserializationContext,
        node: JsonNode
    ): WorkflowParseConfig {
        // TODO: Implement concrete parse config classes
        return ctxt.reportInputMismatch(
            WorkflowParseConfig::class.java,
            "Deserialization for PARSE category is not yet implemented."
        )
    }

    /**
     * Deserializes TRIGGER category configs.
     * Routes to concrete trigger types: SCHEDULE, ENTITY_EVENT, WEBHOOK, FUNCTION.
     */
    private fun deserializeTriggerConfig(
        p: JsonParser,
        ctxt: DeserializationContext,
        node: JsonNode
    ): WorkflowTriggerConfig {
        val subType = ctxt.getEnumFromField<WorkflowTriggerType>(
            node,
            "subType",
            WorkflowTriggerConfig::class.java
        )

        return when (subType) {
            WorkflowTriggerType.ENTITY_EVENT -> ctxt.readTreeAsValue(node, WorkflowEntityEventTriggerConfig::class.java)
            WorkflowTriggerType.SCHEDULE -> ctxt.readTreeAsValue(node, WorkflowScheduleTriggerConfig::class.java)
            WorkflowTriggerType.WEBHOOK -> ctxt.readTreeAsValue(node, WorkflowWebhookTriggerConfig::class.java)
            WorkflowTriggerType.FUNCTION -> ctxt.readTreeAsValue(node, WorkflowFunctionTriggerConfig::class.java)
        }
    }

    /**
     * Deserializes ACTION category configs.
     * Routes to concrete action types: CREATE_ENTITY, UPDATE_ENTITY, DELETE_ENTITY, QUERY_ENTITY, HTTP_REQUEST.
     */
    private fun deserializeActionConfig(
        p: JsonParser,
        ctxt: DeserializationContext,
        node: JsonNode
    ): WorkflowActionConfig {
        val subType = ctxt.getEnumFromField<WorkflowActionType>(
            node,
            "subType",
            WorkflowActionConfig::class.java
        )

        return when (subType) {
            WorkflowActionType.CREATE_ENTITY -> ctxt.readTreeAsValue(node, WorkflowCreateEntityActionConfig::class.java)
            WorkflowActionType.UPDATE_ENTITY -> ctxt.readTreeAsValue(node, WorkflowUpdateEntityActionConfig::class.java)
            WorkflowActionType.DELETE_ENTITY -> ctxt.readTreeAsValue(node, WorkflowDeleteEntityActionConfig::class.java)
            WorkflowActionType.QUERY_ENTITY -> ctxt.readTreeAsValue(node, WorkflowQueryEntityActionConfig::class.java)
            WorkflowActionType.HTTP_REQUEST -> ctxt.readTreeAsValue(node, WorkflowHttpRequestActionConfig::class.java)
            else -> TODO()
        }
    }

    /**
     * Deserializes CONTROL_FLOW category configs.
     * Routes to concrete control types: CONDITION (more in future phases).
     */
    private fun deserializeControlConfig(
        p: JsonParser,
        ctxt: DeserializationContext,
        node: JsonNode
    ): WorkflowControlConfig {
        val subType = ctxt.getEnumFromField<WorkflowControlType>(
            node,
            "subType",
            WorkflowControlConfig::class.java
        )

        return when (subType) {
            WorkflowControlType.CONDITION -> ctxt.readTreeAsValue(node, WorkflowConditionControlConfig::class.java)
            // TODO: Add SWITCH, LOOP, PARALLEL in Phase 5+
            else -> ctxt.reportInputMismatch(
                WorkflowControlConfig::class.java,
                "Deserialization for CONTROL_FLOW subType '$subType' is not yet implemented."
            )
        }
    }


    /**
     * Deserializes UTILITY category configs.
     * TODO: Implement concrete utility config classes.
     */
    private fun deserializeUtilityConfig(
        p: JsonParser,
        ctxt: DeserializationContext,
        node: JsonNode
    ): WorkflowUtilityConfig {
        val subType = ctxt.getEnumFromField<WorkflowUtilityActionType>(
            node,
            "subType",
            WorkflowUtilityConfig::class.java
        )

        // TODO: Implement concrete utility config classes
        return ctxt.reportInputMismatch(
            WorkflowUtilityConfig::class.java,
            "Deserialization for UTILITY subType '$subType' is not yet implemented."
        )
    }

    /**
     * Deserializes FUNCTION category configs.
     * FUNCTION has no subtypes - single concrete implementation.
     */
    private fun deserializeFunctionConfig(
        p: JsonParser,
        ctxt: DeserializationContext,
        node: JsonNode
    ): WorkflowFunctionConfig {
        // FUNCTION category has no subtypes
        // Return the single concrete implementation
        return ctxt.readTreeAsValue(node, WorkflowFunctionConfig::class.java)
    }
}
