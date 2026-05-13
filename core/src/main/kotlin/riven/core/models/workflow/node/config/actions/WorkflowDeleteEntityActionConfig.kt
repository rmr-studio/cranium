package cranium.core.models.workflow.node.config.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.common.icon.IconType
import cranium.core.enums.workflow.WorkflowActionType
import cranium.core.enums.workflow.WorkflowNodeConfigFieldType
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.workflow.engine.state.DeleteEntityOutput
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.NodeServiceProvider
import cranium.core.models.workflow.node.config.WorkflowActionConfig
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.validation.ConfigValidationResult
import cranium.core.models.workflow.node.service
import cranium.core.enums.entity.EntitySelectType
import cranium.core.models.request.entity.DeleteEntityRequest
import cranium.core.service.entity.EntityService
import cranium.core.service.workflow.state.WorkflowNodeConfigValidationService
import java.util.*

private val log = KotlinLogging.logger {}

/**
 * Configuration for DELETE_ENTITY action nodes.
 *
 * ## Configuration Properties
 *
 * @property entityId UUID of the entity to delete (template-enabled)
 * @property timeoutSeconds Optional timeout override in seconds
 *
 * ## Example Configuration
 *
 * ```json
 * {
 *   "version": 1,
 *   "type": "ACTION",
 *   "subType": "DELETE_ENTITY",
 *   "entityId": "{{ steps.find_expired_record.output.entityId }}"
 * }
 * ```
 *
 * ## Output
 *
 * Returns map with:
 * - `entityId`: UUID of deleted entity
 * - `deleted`: Boolean true
 * - `impactedEntities`: Int count of entities affected by cascade
 */
@Schema(
    name = "WorkflowDeleteEntityActionConfig",
    description = "Configuration for DELETE_ENTITY action nodes."
)
@JsonTypeName("workflow_delete_entity_action")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowDeleteEntityActionConfig(
    override val version: Int = 1,

    @param:Schema(
        description = "UUID of the entity to delete. Can be a static UUID or template.",
        example = "550e8400-e29b-41d4-a716-446655440000"
    )
    val entityId: String,

    @param:Schema(
        description = "Optional timeout override in seconds",
        nullable = true
    )
    val timeoutSeconds: Long? = null

) : WorkflowActionConfig {

    override val type: WorkflowNodeType
        get() = WorkflowNodeType.ACTION

    override val subType: WorkflowActionType
        get() = WorkflowActionType.DELETE_ENTITY

    /**
     * Returns typed fields as a map for template resolution.
     * Used by WorkflowCoordinationService to resolve templates before execution.
     */
    override val config: Map<String, Any?>
        get() = mapOf(
            "entityId" to entityId,
            "timeoutSeconds" to timeoutSeconds
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "Delete Entity",
            description = "Deletes an entity instance",
            icon = IconType.TRASH_2,
            category = WorkflowNodeType.ACTION
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "entityId",
                label = "Entity ID",
                type = WorkflowNodeConfigFieldType.UUID,
                required = true,
                description = "UUID of the entity to delete"
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
     * - timeout is non-negative if provided
     */
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        /**
         * TODO: Add validation for checking if the entity exists before deletion.
         * **/

        val validationService: WorkflowNodeConfigValidationService =
            injector.service<WorkflowNodeConfigValidationService>()

        return validationService.combine(
            validationService.validateTemplateOrUuid(entityId, "entityId"),
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

        log.info { "Deleting entity: $resolvedEntityId in workspace: ${dataStore.metadata.workspaceId}" }

        // Get EntityService on-demand
        val entityService = services.service<EntityService>()

        // Execute deletion via EntityService
        val result = entityService.deleteEntities(
            dataStore.metadata.workspaceId,
            DeleteEntityRequest(
                type = EntitySelectType.BY_ID,
                entityIds = listOf(resolvedEntityId),
            )
        )

        // Return typed output
        return DeleteEntityOutput(
            entityId = resolvedEntityId,
            deleted = true,
            impactedEntities = result.updatedEntities?.size ?: 0
        )
    }
}
