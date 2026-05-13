package cranium.core.models.workflow.node.config.trigger

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowNodeConfigFieldType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.enums.workflow.WorkflowTriggerType
import cranium.core.models.common.json.JsonObject
import cranium.core.models.common.validation.Schema
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.WorkflowTriggerConfig
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import io.swagger.v3.oas.annotations.media.Schema as SwaggerSchema

/**
 * Configuration for FUNCTION trigger nodes.
 *
 * Triggers workflow execution when called as a function
 * with the specified input schema.
 */
@SwaggerSchema(
    name = "WorkflowFunctionTriggerConfig",
    description = "Configuration for FUNCTION trigger nodes. Triggers workflow execution when called as a function."
)
@JsonTypeName("workflow_function_trigger")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowFunctionTriggerConfig(
    override val version: Int = 1,
    val schema: Schema<String>
) : WorkflowTriggerConfig {
    override val subType: WorkflowTriggerType
        get() = WorkflowTriggerType.FUNCTION

    override val config: JsonObject
        get() = mapOf(
            "schema" to schema
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Function Call",
            description = "Triggers when called programmatically",
            icon = IconType.CODE,
            category = WorkflowNodeType.TRIGGER
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "schema",
                label = "Input Schema",
                type = WorkflowNodeConfigFieldType.JSON,
                required = true,
                description = "Schema defining the expected input structure for the function"
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - schema is provided (already non-null in constructor)
     * - schema has valid structure
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    @Suppress("UNUSED_PARAMETER")
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        // schema is non-null in constructor, so it's always present
        // Could add deeper schema validation here if needed
        return ConfigValidationResult.valid()
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: Map<String, Any?>,
        services: NodeServiceProvider
    ): NodeOutput {
        // Triggers are entry points, not executed during workflow
        throw UnsupportedOperationException("TRIGGER nodes don't execute during workflow")
    }
}