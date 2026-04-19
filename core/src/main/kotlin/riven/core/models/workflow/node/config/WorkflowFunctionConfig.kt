package riven.core.models.workflow.node.config

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.enums.common.icon.IconType
import riven.core.enums.workflow.WorkflowNodeType
import riven.core.models.common.json.JsonObject
import riven.core.models.workflow.engine.state.NodeOutput
import riven.core.models.workflow.engine.state.WorkflowDataStore
import riven.core.models.workflow.node.NodeServiceProvider
import riven.core.models.workflow.node.config.validation.ConfigValidationResult

/**
 * Configuration for a FUNCTION category node.
 *
 * A function defines a closed set of operations that can be executed.
 * A workflow is composed of multiple functions that are orchestrated
 * together to achieve a specific goal.
 *
 * FUNCTION has no subtypes - it's a single concrete implementation.
 */
@Schema(
    name = "WorkflowFunctionConfig",
    description = "Configuration for FUNCTION category nodes."
)
@JsonTypeName("function_config")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowFunctionConfig(
    override val version: Int = 1
) : WorkflowNodeConfig {
    override val type: WorkflowNodeType
        get() = WorkflowNodeType.FUNCTION

    override val config: JsonObject
        get() = emptyMap()

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Function",
            description = "A reusable workflow function",
            icon = IconType.SQUARE_FUNCTION,
            category = WorkflowNodeType.FUNCTION
        )

        val configSchema: List<WorkflowNodeConfigField> = emptyList()
    }

    /**
     * Validates this configuration.
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    @Suppress("UNUSED_PARAMETER")
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        // FUNCTION nodes have no configurable properties to validate
        return ConfigValidationResult.valid()
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: Map<String, Any?>,
        services: NodeServiceProvider
    ): NodeOutput {
        // TODO: Implement FUNCTION node execution in Phase 5+
        throw UnsupportedOperationException("FUNCTION nodes not implemented in Phase 4.1")
    }
}