package riven.core.models.workflow.node.config.trigger

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.enums.common.icon.IconType
import riven.core.enums.util.OperationType
import riven.core.enums.workflow.WorkflowNodeConfigFieldType
import riven.core.enums.workflow.WorkflowNodeType
import riven.core.enums.workflow.WorkflowTriggerType
import riven.core.models.common.json.JsonObject
import riven.core.models.workflow.engine.state.NodeOutput
import riven.core.models.workflow.engine.state.WorkflowDataStore
import riven.core.models.workflow.node.NodeServiceProvider
import riven.core.models.workflow.node.config.WorkflowNodeConfigField
import riven.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import riven.core.models.workflow.node.config.WorkflowTriggerConfig
import riven.core.models.workflow.node.config.validation.ConfigValidationError
import riven.core.models.workflow.node.config.validation.ConfigValidationResult
import riven.core.models.workflow.node.service
import riven.core.service.workflow.state.WorkflowNodeConfigValidationService

/**
 * Configuration for ENTITY_EVENT trigger nodes.
 *
 * Triggers workflow execution when entity operations occur
 * (CREATE, UPDATE, DELETE) on specified entity types.
 */
@Schema(
    name = "WorkflowEntityEventTriggerConfig",
    description = "Configuration for ENTITY_EVENT trigger nodes. Triggers workflow execution when entity operations occur."
)
@JsonTypeName("workflow_entity_event_trigger")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowEntityEventTriggerConfig(
    override val version: Int = 1,
    val key: String,
    val operation: OperationType,
    val field: List<String> = emptyList(),
    val expressions: Any
) : WorkflowTriggerConfig {

    override val subType: WorkflowTriggerType
        get() = WorkflowTriggerType.ENTITY_EVENT

    override val config: JsonObject = mapOf(
        "key" to key,
        "operation" to operation.name,
        "field" to field,
        "expressions" to expressions
    )


    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Entity Event",
            description = "Triggers when an entity is created, updated, or deleted",
            icon = IconType.DATABASE,
            category = WorkflowNodeType.TRIGGER
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "key",
                label = "Entity Type Key",
                type = WorkflowNodeConfigFieldType.STRING,
                required = true,
                description = "Key of the entity type to watch for events"
            ),
            WorkflowNodeConfigField(
                key = "operation",
                label = "Operation",
                type = WorkflowNodeConfigFieldType.ENUM,
                required = true,
                description = "Entity operation to trigger on",
                options = mapOf(
                    "CREATE" to "Create",
                    "UPDATE" to "Update",
                    "DELETE" to "Delete"
                )
            ),
            WorkflowNodeConfigField(
                key = "field",
                label = "Fields",
                type = WorkflowNodeConfigFieldType.JSON,
                required = false,
                description = "Specific fields to watch for changes"
            ),
            WorkflowNodeConfigField(
                key = "expressions",
                label = "Expressions",
                type = WorkflowNodeConfigFieldType.JSON,
                required = true,
                description = "Filter expressions for triggering"
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - key is not blank (entity type key)
     * - operation is valid (already enforced by enum)
     * - expressions is provided (not null)
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        val validationService = injector.service<WorkflowNodeConfigValidationService>()
        val errors = mutableListOf<ConfigValidationError>()

        // Validate key (entity type key)
        errors.addAll(validationService.validateRequiredString(key, "key"))

        // operation is an enum, so it's always valid if deserialized

        // expressions should not be null (it's typed as Any which is non-null in Kotlin)
        // but validate it has content if it's a string or collection
        when (expressions) {
            is String -> {
                if ((expressions as String).isBlank()) {
                    errors.add(ConfigValidationError("expressions", "Expressions cannot be blank"))
                }
            }

            is Collection<*> -> {
                if ((expressions as Collection<*>).isEmpty()) {
                    errors.add(ConfigValidationError("expressions", "Expressions list cannot be empty"))
                }
            }
        }

        return ConfigValidationResult(errors)
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
