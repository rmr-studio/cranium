package cranium.core.models.workflow.node.config

import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.deserializer.WorkflowNodeConfigDeserializer
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.common.json.JsonObject
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult

/**
 * Extensible configuration interface for workflow nodes.
 *
 * Controls the behavior of a workflow node during execution.
 * Subtypes are automatically discovered via [cranium.core.configuration.openapi.WorkflowNodeConfigSchemaCustomizer].
 *
 * @property type Node type (ACTION, CONTROL_FLOW, etc.)
 * @property version Schema version for node configuration
 */
@Schema(
    description = "Polymorphic workflow node configuration. Discriminated by 'type' and 'subType' fields."
)
@JsonDeserialize(using = WorkflowNodeConfigDeserializer::class)
sealed interface WorkflowNodeConfig {
    val type: WorkflowNodeType
    val version: Int

    /**
     * Execute this node with given datastore and resolved inputs.
     *
     * Implementation contract:
     * - Actions: Return typed NodeOutput (CreateEntityOutput, HttpResponseOutput, etc.)
     * - Controls: Return typed NodeOutput (ConditionOutput, etc.)
     * - Loops: Return typed NodeOutput (Phase 5+)
     * - Switch: Return typed NodeOutput (Phase 5+)
     *
     * @param dataStore Unified workflow data store with step outputs, trigger context, and metadata
     * @param inputs Resolved inputs (templates already converted to values)
     * @param services Service provider for on-demand access to Spring services
     * @return Typed NodeOutput representing execution result
     * @throws Exception on execution failure (caught by activity implementation)
     */
    fun execute(
        dataStore: WorkflowDataStore,
        inputs: JsonObject,
        services: NodeServiceProvider
    ): NodeOutput


    /*
    * Node attribute configuration properties and validation
    */

    /**
     * Validate configuration using provided services.
     *
     * @param injector Service provider for validation needs
     *  - This allows a node to inject spring managed services to perform additional application based validation
     * @return Validation result with success/failure and messages
     */
    fun validate(injector: NodeServiceProvider): ConfigValidationResult

    /**
     * Get raw configuration values as JsonObject.
     */
    val config: JsonObject

    /**
     * Defines the schema for this node configuration.
     * Used for generating OpenAPI documentation and validation in order to
     * create dynamic UIs for node configuration.
     */
    val configSchema: List<WorkflowNodeConfigField>
}
