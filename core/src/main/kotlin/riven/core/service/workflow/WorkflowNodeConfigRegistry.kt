package cranium.core.service.workflow

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.workflow.node.config.WorkflowFunctionConfig
import cranium.core.models.workflow.node.config.WorkflowNodeConfig
import cranium.core.models.workflow.node.config.WorkflowNodeConfigField
import cranium.core.models.workflow.node.config.WorkflowNodeOutputMetadata
import cranium.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import cranium.core.models.workflow.node.config.actions.*
import cranium.core.models.workflow.node.config.controls.WorkflowConditionControlConfig
import cranium.core.models.workflow.node.config.trigger.WorkflowEntityEventTriggerConfig
import cranium.core.models.workflow.node.config.trigger.WorkflowFunctionTriggerConfig
import cranium.core.models.workflow.node.config.trigger.WorkflowScheduleTriggerConfig
import cranium.core.models.workflow.node.config.trigger.WorkflowWebhookTriggerConfig
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

private val logger = KotlinLogging.logger {}

/**
 * Registry entry for a workflow node configuration schema.
 *
 * @property type The node category (ACTION, TRIGGER, CONTROL_FLOW, etc.)
 * @property subType The specific node subtype (CREATE_ENTITY, WEBHOOK, CONDITION, etc.)
 * @property configClass The KClass of the configuration
 * @property schema The configuration schema fields
 * @property metadata Display metadata for the node type (label, description, icon)
 */
data class NodeSchemaEntry(
    val type: WorkflowNodeType,
    val subType: String,
    val configClass: KClass<out WorkflowNodeConfig>,
    val schema: List<WorkflowNodeConfigField>,
    val metadata: WorkflowNodeTypeMetadata,
    val outputMetadata: WorkflowNodeOutputMetadata? = null
)

/**
 * Response model for node schema with metadata (without configClass).
 * Used for API responses.
 */
data class WorkflowNodeMetadata(
    val type: WorkflowNodeType,
    val subType: String,
    val metadata: WorkflowNodeTypeMetadata,
    val schema: List<WorkflowNodeConfigField>,
    val outputMetadata: WorkflowNodeOutputMetadata? = null
)

/**
 * Registry service that discovers and caches all workflow node configuration schemas.
 *
 * This service provides static access to the configuration schemas for all
 * workflow node types without requiring instantiation of the config classes.
 *
 * ## Usage
 *
 * ```kotlin
 * val registry = WorkflowNodeConfigRegistry()
 * val allSchemas = registry.getAllSchemas()
 * val actionSchemas = registry.getSchemasByType(WorkflowNodeType.ACTION)
 * val createEntitySchema = registry.getSchema("ACTION", "CREATE_ENTITY")
 * ```
 *
 * ## Adding New Nodes
 *
 * To add a new node type:
 * 1. Create the config class extending the appropriate interface (WorkflowActionConfig, etc.)
 * 2. Add a companion object with `val configSchema: List<WorkflowNodeConfigField> and `val metadata: WorkflowNodeTypeMetadata`
 * 3. Register the config class in [registerAllNodes]
 *
 * The schema will then be automatically available via this registry.
 */
@Service
class WorkflowNodeConfigRegistry {

    /**
     * Cached registry entries, initialized on first access.
     */
    private val entries: List<NodeSchemaEntry> by lazy {
        logger.info { "Initializing WorkflowNodeConfigRegistry..." }
        val registered = registerAllNodes()
        logger.info { "Registered ${registered.size} workflow node configuration schemas" }
        registered
    }

    /**
     * Returns all registered node configuration schemas.
     *
     * @return Map of node key (e.g., "ACTION.CREATE_ENTITY") to schema fields
     */
    fun getAllNodes(): Map<String, WorkflowNodeMetadata> {
        return entries.associate {
            "${it.type}.${it.subType}" to WorkflowNodeMetadata(
                type = it.type,
                subType = it.subType,
                metadata = it.metadata,
                schema = it.schema,
                outputMetadata = it.outputMetadata
            )
        }
    }


    /**
     * Returns all registered node schema entries.
     *
     * @return List of all node schema entries with full metadata
     */
    fun getAllEntries(): List<NodeSchemaEntry> = entries

    /**
     * Returns node schemas filtered by node type.
     *
     * @param type The node type to filter by
     * @return List of schema entries for the specified type
     */
    fun getSchemasByType(type: WorkflowNodeType): List<NodeSchemaEntry> {
        return entries.filter { it.type == type }
    }

    /**
     * Returns the schema for a specific node type and subtype.
     *
     * @param type The node type (e.g., "ACTION")
     * @param subType The node subtype (e.g., "CREATE_ENTITY")
     * @return The schema fields, or null if not found
     */
    fun getSchema(type: String, subType: String): List<WorkflowNodeConfigField>? {
        return entries.find {
            it.type.name == type && it.subType == subType
        }?.schema
    }

    /**
     * Returns the schema for a specific node by its full key.
     *
     * @param key The full node key (e.g., "ACTION.CREATE_ENTITY")
     * @return The schema fields, or null if not found
     */
    fun getSchemaByKey(key: String): List<WorkflowNodeConfigField>? {
        val parts = key.split(".", limit = 2)
        if (parts.size != 2) return null
        return getSchema(parts[0], parts[1])
    }

    /**
     * Registers all known workflow node configuration classes.
     *
     * This method explicitly registers each config class to extract its schema.
     * When adding new node types, add them here.
     */
    private fun registerAllNodes(): List<NodeSchemaEntry> {
        return listOfNotNull(
            // Action nodes
            registerNode<WorkflowCreateEntityActionConfig>(
                WorkflowNodeType.ACTION,
                "CREATE_ENTITY"
            ),
            registerNode<WorkflowDeleteEntityActionConfig>(
                WorkflowNodeType.ACTION,
                "DELETE_ENTITY"
            ),
            registerNode<WorkflowUpdateEntityActionConfig>(
                WorkflowNodeType.ACTION,
                "UPDATE_ENTITY"
            ),
            registerNode<WorkflowQueryEntityActionConfig>(
                WorkflowNodeType.ACTION,
                "QUERY_ENTITY"
            ),
            registerNode<WorkflowBulkUpdateEntityActionConfig>(
                WorkflowNodeType.ACTION,
                "BULK_UPDATE_ENTITY"
            ),
            registerNode<WorkflowHttpRequestActionConfig>(
                WorkflowNodeType.ACTION,
                "HTTP_REQUEST"
            ),

            // Trigger nodes
            registerNode<WorkflowEntityEventTriggerConfig>(
                WorkflowNodeType.TRIGGER,
                "ENTITY_EVENT"
            ),
            registerNode<WorkflowScheduleTriggerConfig>(
                WorkflowNodeType.TRIGGER,
                "SCHEDULE"
            ),
            registerNode<WorkflowWebhookTriggerConfig>(
                WorkflowNodeType.TRIGGER,
                "WEBHOOK"
            ),
            registerNode<WorkflowFunctionTriggerConfig>(
                WorkflowNodeType.TRIGGER,
                "FUNCTION"
            ),

            // Control flow nodes
            registerNode<WorkflowConditionControlConfig>(
                WorkflowNodeType.CONTROL_FLOW,
                "CONDITION"
            ),

            // Function nodes
            registerNode<WorkflowFunctionConfig>(
                WorkflowNodeType.FUNCTION,
                "FUNCTION"
            )
        )
    }

    /**
     * Registers a single node configuration class by extracting its companion object schema and metadata.
     *
     * @param type The node type category
     * @param subType The specific node subtype
     * @return NodeSchemaEntry if successful, null if schema extraction fails
     */
    private inline fun <reified T : WorkflowNodeConfig> registerNode(
        type: WorkflowNodeType,
        subType: String
    ): NodeSchemaEntry? {
        return try {
            val companion = T::class.companionObjectInstance
            if (companion == null) {
                logger.warn { "No companion object found for ${T::class.simpleName}" }
                return null
            }

            val schemaProperty = companion::class.members.find { it.name == "configSchema" }
            if (schemaProperty == null) {
                logger.warn { "No configSchema property found in companion of ${T::class.simpleName}" }
                return null
            }

            @Suppress("UNCHECKED_CAST")
            val schema = schemaProperty.call(companion) as? List<WorkflowNodeConfigField>
            if (schema == null) {
                logger.warn { "configSchema is null or wrong type for ${T::class.simpleName}" }
                return null
            }

            val metadataProperty = companion::class.members.find { it.name == "metadata" }
            if (metadataProperty == null) {
                logger.warn { "No metadata property found in companion of ${T::class.simpleName}" }
                return null
            }

            val metadata = metadataProperty.call(companion) as? WorkflowNodeTypeMetadata
            if (metadata == null) {
                logger.warn { "metadata is null or wrong type for ${T::class.simpleName}" }
                return null
            }

            // Extract outputMetadata (optional during rollout)
            val outputMetadataProperty = companion::class.members.find { it.name == "outputMetadata" }
            val outputMetadata = outputMetadataProperty?.call(companion) as? WorkflowNodeOutputMetadata

            logger.debug { "Registered ${T::class.simpleName}: $type.$subType with ${schema.size} fields" }

            NodeSchemaEntry(
                type = type,
                subType = subType,
                configClass = T::class,
                schema = schema,
                metadata = metadata,
                outputMetadata = outputMetadata
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to register node config ${T::class.simpleName}" }
            null
        }
    }
}
