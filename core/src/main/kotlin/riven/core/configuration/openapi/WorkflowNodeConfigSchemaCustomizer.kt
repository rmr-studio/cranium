package cranium.core.configuration.openapi

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.stereotype.Component
import cranium.core.service.workflow.WorkflowNodeConfigRegistry

/**
 * OpenAPI customizer that registers all [cranium.core.models.workflow.node.config.WorkflowNodeConfig]
 * implementations for OpenAPI schema generation.
 *
 * ## Why This Exists
 *
 * SpringDoc/OpenAPI doesn't automatically discover polymorphic subtypes when using
 * a custom Jackson deserializer. This customizer bridges that gap by using the
 * [WorkflowNodeConfigRegistry] to get all registered implementations.
 *
 * ## Adding New Node Types
 *
 * When you create a new workflow node config implementation:
 *
 * 1. Add `@Schema(name = "YourNewConfig", description = "...")` to the class
 * 2. Add a companion object with `val configSchema: List<WorkflowNodeConfigField>`
 * 3. Register in [WorkflowNodeConfigRegistry.registerAllNodes]
 * 4. Update [cranium.core.deserializer.WorkflowNodeConfigDeserializer] to handle deserialization
 *
 * The OpenAPI schema will be automatically generated from the registry.
 *
 * ## Example
 *
 * ```kotlin
 * // 1. Create new config with @Schema annotation and companion object schema
 * @Schema(name = "WorkflowMyNewActionConfig", description = "...")
 * @JsonTypeName("workflow_my_new_action")
 * data class WorkflowMyNewActionConfig(...) : WorkflowActionConfig {
 *     override val configSchema get() = Companion.configSchema
 *
 *     companion object {
 *         val configSchema = listOf(...)
 *     }
 * }
 *
 * // 2. Register in WorkflowNodeConfigRegistry.registerAllNodes()
 * registerNode<WorkflowMyNewActionConfig>(WorkflowNodeType.ACTION, "MY_NEW_ACTION")
 *
 * // 3. Update WorkflowNodeConfigDeserializer.deserializeActionConfig()
 * ```
 */
@Component
class WorkflowNodeConfigSchemaCustomizer(
    private val workflowNodeConfigRegistry: WorkflowNodeConfigRegistry
) : OpenApiCustomizer {

    override fun customise(openApi: OpenAPI) {
        val components = openApi.components ?: return

        val allConfigClasses = workflowNodeConfigRegistry.getAllEntries().map { it.configClass }

        if (allConfigClasses.isEmpty()) {
            return
        }

        // Get the model converter to generate schemas for each config class
        val modelConverters = ModelConverters.getInstance()

        // Generate and register schemas for each config class
        val oneOfSchemas = mutableListOf<Schema<Any>>()

        for (configClass in allConfigClasses) {
            val schemaName = configClass.simpleName ?: continue

            // Generate schema for this class using ModelConverters
            val resolvedSchemas = modelConverters.readAll(AnnotatedType(configClass.java))

            // Add all resolved schemas to components (includes the main schema and any nested types)
            resolvedSchemas.forEach { (name, schema) ->
                components.addSchemas(name, schema)
            }

            // Add reference to the oneOf list
            oneOfSchemas.add(Schema<Any>().`$ref`("#/components/schemas/$schemaName"))
        }

        // Create composed schema with oneOf
        val composedSchema = ComposedSchema().apply {
            oneOf = oneOfSchemas
            discriminator = Discriminator().apply {
                propertyName = "type"
            }
            description = "Polymorphic workflow node configuration. Discriminated by 'type' and 'subType' fields."
        }

        // Register the parent schema
        components.addSchemas("WorkflowNodeConfig", composedSchema)
    }
}
