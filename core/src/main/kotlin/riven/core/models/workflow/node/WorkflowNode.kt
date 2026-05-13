package cranium.core.models.workflow.node

import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.common.json.JsonObject
import cranium.core.models.workflow.engine.state.NodeOutput
import cranium.core.models.workflow.engine.state.WorkflowDataStore
import cranium.core.models.workflow.node.config.WorkflowNodeConfig
import java.util.*

/**
 * Runtime representation of a workflow node ready for execution.
 *
 * This DTO combines entity metadata (id, workspaceId, key) with the pure
 * node configuration ([cranium.core.models.workflow.node.config.WorkflowNodeConfig]) to provide a complete execution context.
 *
 * ## Three-Layer Architecture
 *
 * 1. **WorkflowNodeConfig** - Pure configuration and execution logic (no ID)
 * 2. **WorkflowNodeEntity** - JPA entity for persistence (nullable ID from DB)
 * 3. **ExecutableNode** (this class) - Runtime DTO with guaranteed non-null ID
 *
 * ## Why This Exists
 *
 * The problem: `WorkflowNodeConfig` can't have an ID because:
 * - IDs are DB-generated during persistence
 * - Config objects are created before saving to the database
 * - The node needs to be serializable to JSONB without the entity's ID
 *
 * The solution: `ExecutableNode` wraps the config with entity metadata:
 * - Created only from persisted entities (ID guaranteed to exist)
 * - Provides execution context (workspaceId for security checks)
 * - Delegates execution to the underlying config
 *
 * ## Usage
 *
 * ```kotlin
 * // Convert entity to executable node
 * val entity: WorkflowNodeEntity = repository.findById(nodeId)
 * val executable: ExecutableNode = entity.toExecutableNode()
 *
 * // Execute with full context
 * if (executable.workspaceId != currentWorkspaceId) {
 *     throw SecurityException("Node does not belong to workspace")
 * }
 * val result = executable.execute(context, inputs, services)
 * ```
 *
 * @property id The database-generated node ID (non-null, from entity)
 * @property workspaceId The workspace this node belongs to (non-null, for security)
 * @property key Unique identifier for this node within the workspace
 * @property name Human-readable display name
 * @property description Optional description of the node's purpose
 * @property config The underlying node configuration with execution logic
 */
data class WorkflowNode(
    val id: UUID,
    val workspaceId: UUID,
    val key: String,
    val name: String,
    val description: String? = null,
    val config: WorkflowNodeConfig
) {
    /**
     * The node type, delegated from config.
     */
    val type: WorkflowNodeType
        get() = config.type

    /**
     * The config schema version, delegated from config.
     */
    val version: Int
        get() = config.version

    /**
     * Execute this node with given datastore and resolved inputs.
     *
     * Delegates to the underlying [WorkflowNodeConfig.execute] method.
     *
     * @param dataStore Unified workflow data store with step outputs, trigger context, and metadata
     * @param inputs Resolved inputs (templates already converted to values)
     * @param services Service provider for on-demand access to Spring services
     * @return Typed NodeOutput representing execution result
     * @throws Exception on execution failure
     */
    fun execute(
        dataStore: WorkflowDataStore,
        inputs: JsonObject,
        services: NodeServiceProvider
    ): NodeOutput = config.execute(dataStore, inputs, services)
}