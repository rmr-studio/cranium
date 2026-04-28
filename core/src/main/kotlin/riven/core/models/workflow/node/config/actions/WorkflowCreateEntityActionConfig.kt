package riven.core.models.workflow.node.config.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import riven.core.enums.common.icon.IconType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.workflow.OutputFieldType
import riven.core.enums.workflow.WorkflowActionType
import riven.core.enums.workflow.WorkflowNodeConfigFieldType
import riven.core.enums.workflow.WorkflowNodeType
import riven.core.models.common.json.JsonObject
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.entity.payload.EntityAttributeRequest
import riven.core.models.request.entity.SaveEntityRequest
import riven.core.models.workflow.engine.state.CreateEntityOutput
import riven.core.models.workflow.engine.state.NodeOutput
import riven.core.models.workflow.engine.state.WorkflowDataStore
import riven.core.models.workflow.node.NodeServiceProvider
import riven.core.models.workflow.node.config.WorkflowActionConfig
import riven.core.models.workflow.node.config.WorkflowNodeConfigField
import riven.core.models.workflow.node.config.WorkflowNodeOutputField
import riven.core.models.workflow.node.config.WorkflowNodeOutputMetadata
import riven.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import riven.core.models.workflow.node.config.validation.ConfigValidationResult
import riven.core.models.workflow.node.service
import riven.core.service.entity.EntityService
import riven.core.service.workflow.state.WorkflowNodeConfigValidationService
import java.util.*

/**
 * Configuration for CREATE_ENTITY action nodes.
 *
 * ## Configuration Properties
 *
 * @property entityTypeId UUID of the entity type to create (template-enabled)
 * @property payload Map of attribute values to set (template-enabled values)
 * @property timeoutSeconds Optional timeout override in seconds
 *
 * ## Example Configuration
 *
 * ```json
 * {
 *   "version": 1,
 *   "type": "ACTION",
 *   "subType": "CREATE_ENTITY",
 *   "entityTypeId": "{{ steps.get_type.output.typeId }}",
 *   "payload": {
 *     "name": "{{ steps.fetch_data.output.clientName }}",
 *     "email": "client@example.com"
 *   }
 * }
 * ```
 *
 * ## Output
 *
 * Returns map with:
 * - `entityId`: UUID of created entity
 * - `entityTypeId`: UUID of entity type
 * - `payload`: Map of entity data
 *
 * Templates are resolved before execute() is called.
 */
@Schema(
    name = "WorkflowCreateEntityActionConfig",
    description = "Configuration for CREATE_ENTITY action nodes."
)
@JsonTypeName("workflow_create_entity_action")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowCreateEntityActionConfig(
    override val version: Int = 1,

    @param:Schema(
        description = "UUID of the entity type to create. Can be a static UUID or template like {{ steps.x.output.typeId }}",
        example = "550e8400-e29b-41d4-a716-446655440000"
    )
    val entityTypeId: String,

    @param:Schema(
        description = "Map of attribute key to value. Values can be templates like {{ steps.x.output.field }}",
        example = """{"name": "{{ steps.fetch.output.name }}", "email": "user@example.com"}"""
    )
    val payload: Map<String, String> = emptyMap(),

    @param:Schema(
        description = "Optional timeout override in seconds",
        example = "30",
        nullable = true
    )
    val timeoutSeconds: Long? = null

) : WorkflowActionConfig {

    override val type: WorkflowNodeType
        get() = WorkflowNodeType.ACTION

    override val subType: WorkflowActionType
        get() = WorkflowActionType.CREATE_ENTITY

    /**
     * Returns typed fields as a map for template resolution.
     * Used by WorkflowCoordinationService to resolve templates before execution.
     */
    override val config: JsonObject
        get() = mapOf(
            "entityTypeId" to entityTypeId,
            "payload" to payload,
            "timeoutSeconds" to timeoutSeconds
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Create Entity",
            description = "Creates a new entity instance",
            icon = IconType.PLUS,
            category = WorkflowNodeType.ACTION
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "entityTypeId",
                label = "Entity Type",
                type = WorkflowNodeConfigFieldType.ENTITY_TYPE,
                required = true,
            ),
            WorkflowNodeConfigField(
                key = "payload",
                label = "Payload",
                type = WorkflowNodeConfigFieldType.KEY_VALUE,
                required = true,
            ),
            WorkflowNodeConfigField(
                key = "timeoutSeconds",
                label = "Timeout (seconds)",
                type = WorkflowNodeConfigFieldType.DURATION,
                required = false,
            )
        )

        val outputMetadata = WorkflowNodeOutputMetadata(
            fields = listOf(
                WorkflowNodeOutputField(
                    key = "entityId",
                    label = "Entity ID",
                    type = OutputFieldType.UUID,
                    exampleValue = "550e8400-e29b-41d4-a716-446655440000"
                ),
                WorkflowNodeOutputField(
                    key = "entityTypeId",
                    label = "Entity Type ID",
                    type = OutputFieldType.UUID,
                    exampleValue = "660e8400-e29b-41d4-a716-446655440001"
                ),
                WorkflowNodeOutputField(
                    key = "payload",
                    label = "Entity Payload",
                    type = OutputFieldType.MAP,
                    description = "Entity attributes as UUID-keyed map",
                    exampleValue = mapOf("770e8400-e29b-41d4-a716-446655440002" to "Example Value")
                )
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - entityTypeId is valid UUID or template
     * - payload values have valid template syntax
     * - timeout is non-negative if provided
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        /** TODO: This would also need to validate the following:
         * - entityTypeId resolves to an existing EntityType in the workspace
         * - payload keys correspond to valid attribute IDs in the EntityType schema
         * - payload values are compatible with the schema types defined in the EntityType and meet all required fields
         * **/


        val validationService = injector.service<WorkflowNodeConfigValidationService>()

        return validationService.combine(
            validationService.validateTemplateOrUuid(entityTypeId, "entityTypeId"),
            validationService.validateTemplateMap(payload, "payload"),
            validationService.validateOptionalDuration(timeoutSeconds, "timeoutSeconds")
        )
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: JsonObject,
        services: NodeServiceProvider
    ): NodeOutput {
        // Extract resolved inputs
        val resolvedEntityTypeId = UUID.fromString(inputs["entityTypeId"] as String)
        val resolvedPayload = inputs["payload"] as? Map<*, *> ?: emptyMap<String, Any?>()

        // Get EntityService on-demand
        val entityService = services.service<EntityService>()

        // Map resolved payload to proper format
        // Keys are UUID strings representing attribute IDs, values are the resolved data
        // Wrap each value in EntityAttributeRequest with TEXT schema type
        // TODO: Infer schema type from entity type schema for proper typing
        @Suppress("UNCHECKED_CAST")
        val entityPayload = resolvedPayload.mapKeys { (key, _) ->
            UUID.fromString(key as String)
        }.mapValues { (_, value) ->
            EntityAttributeRequest(
                EntityAttributePrimitivePayload(
                    value = value,
                    schemaType = SchemaType.TEXT  // Default to TEXT, infer from schema later
                )
            )
        }

        // Create entity via EntityService
        val saveRequest = SaveEntityRequest(
            id = null, // New entity
            payload = entityPayload,
            icon = null
        )

        val result = entityService.saveEntity(
            dataStore.metadata.workspaceId,
            resolvedEntityTypeId,
            saveRequest
        )

        // Return typed output
        return CreateEntityOutput(
            entityId = result.entity?.id ?: throw IllegalStateException("Entity creation failed: no entity returned"),
            entityTypeId = result.entity?.typeId ?: resolvedEntityTypeId,
            payload = result.entity?.payload ?: emptyMap()
        )
    }
}
