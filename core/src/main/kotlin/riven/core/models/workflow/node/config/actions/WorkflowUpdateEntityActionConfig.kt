package cranium.core.models.workflow.node.config.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.workflow.WorkflowActionType
import cranium.core.enums.workflow.WorkflowNodeConfigFieldType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.models.entity.payload.EntityAttributeRequest
import cranium.core.models.request.entity.SaveEntityRequest
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.UpdateEntityOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.WorkflowActionConfig
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import cranium.core.models.workflow.node.service
import cranium.core.service.entity.EntityService
import cranium.core.service.workflow.state.WorkflowNodeConfigValidationService
import java.util.*

/**
 * Configuration for UPDATE_ENTITY action nodes.
 *
 * ## Configuration Properties
 *
 * @property entityId UUID of the entity to update (template-enabled)
 * @property payload Map of attribute values to update (template-enabled values)
 * @property timeoutSeconds Optional timeout override in seconds
 *
 * ## Example Configuration
 *
 * ```json
 * {
 *   "version": 1,
 *   "type": "ACTION",
 *   "subType": "UPDATE_ENTITY",
 *   "entityId": "{{ steps.find_client.output.entityId }}",
 *   "payload": {
 *     "status": "active",
 *     "lastContacted": "{{ steps.get_timestamp.output.now }}"
 *   }
 * }
 * ```
 *
 * ## Output
 *
 * Returns map with:
 * - `entityId`: UUID of updated entity
 * - `updated`: Boolean true
 * - `payload`: Map of entity data after update
 */
@Schema(
    name = "WorkflowUpdateEntityActionConfig",
    description = "Configuration for UPDATE_ENTITY action nodes."
)
@JsonTypeName("workflow_update_entity_action")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowUpdateEntityActionConfig(
    override val version: Int = 1,

    @Schema(
        description = "UUID of the entity to update. Can be a static UUID or template like {{ steps.x.output.entityId }}",
        example = "550e8400-e29b-41d4-a716-446655440000"
    )
    val entityId: String,

    @Schema(
        description = "Map of attribute key to value to update. Values can be templates.",
        example = """{"status": "active", "name": "{{ steps.fetch.output.name }}"}"""
    )
    val payload: Map<String, String> = emptyMap(),

    @Schema(
        description = "Optional timeout override in seconds",
        nullable = true
    )
    val timeoutSeconds: Long? = null

) : WorkflowActionConfig {

    override val type: WorkflowNodeType
        get() = WorkflowNodeType.ACTION

    override val subType: WorkflowActionType
        get() = WorkflowActionType.UPDATE_ENTITY

    /**
     * Returns typed fields as a map for template resolution.
     * Used by WorkflowCoordinationService to resolve templates before execution.
     */
    override val config: Map<String, Any?>
        get() = mapOf(
            "entityId" to entityId,
            "payload" to payload,
            "timeoutSeconds" to timeoutSeconds
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Update Entity",
            description = "Updates an existing entity instance",
            icon = IconType.PENCIL,
            category = WorkflowNodeType.ACTION
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "entityId",
                label = "Entity ID",
                type = WorkflowNodeConfigFieldType.UUID,
                required = true,
                description = "UUID of the entity to update"
            ),
            WorkflowNodeConfigField(
                key = "payload",
                label = "Payload",
                type = WorkflowNodeConfigFieldType.KEY_VALUE,
                required = true,
                description = "Map of attribute values to update"
            ),
            WorkflowNodeConfigField(
                key = "timeoutSeconds",
                label = "Timeout (seconds)",
                type = WorkflowNodeConfigFieldType.DURATION,
                required = false,
                description = "Optional timeout override in seconds"
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - entityId is valid UUID or template
     * - payload values have valid template syntax
     * - timeout is non-negative if provided
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        val validationService = injector.service<WorkflowNodeConfigValidationService>()

        return validationService.combine(
            validationService.validateTemplateOrUuid(entityId, "entityId"),
            validationService.validateTemplateMap(payload, "payload"),
            validationService.validateOptionalDuration(timeoutSeconds, "timeoutSeconds")
        )
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: Map<String, Any?>,
        services: NodeServiceProvider
    ): NodeOutput {
        // Extract resolved inputs
        val resolvedEntityId = UUID.fromString(inputs["entityId"] as String)
        val resolvedPayload = inputs["payload"] as? Map<*, *> ?: emptyMap<String, Any?>()

        // Get EntityService on-demand
        val entityService = services.service<EntityService>()

        // Get existing entity to determine type
        val existingEntity = entityService.getEntity(resolvedEntityId)

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

        // Update entity via EntityService
        val saveRequest = SaveEntityRequest(
            id = resolvedEntityId,
            payload = entityPayload,
            icon = null
        )

        val result = entityService.saveEntity(
            dataStore.metadata.workspaceId,
            existingEntity.typeId,
            saveRequest
        )

        // Return typed output
        return UpdateEntityOutput(
            entityId = resolvedEntityId,
            updated = true,
            payload = result.entity?.payload ?: emptyMap()
        )
    }
}
