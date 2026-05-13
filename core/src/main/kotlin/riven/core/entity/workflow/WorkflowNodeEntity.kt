package cranium.core.entity.workflow

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.enums.workflow.WorkflowNodeType
import cranium.core.models.workflow.node.WorkflowNode
import cranium.core.models.workflow.node.config.WorkflowNodeConfig
import java.util.*

/**
 * JPA entity for storing workflow nodes with polymorphic configuration.
 *
 * The `config` field stores the [WorkflowNodeConfig] as JSONB, which is deserialized
 * to the correct concrete type (e.g., ScheduleTriggerConfig) by WorkflowNodeConfigDeserializer.
 *
 * This entity uses immutable copy-on-write versioning pattern (like BlockTypeEntity):
 * - Creating: version=1, source_id=null
 * - Updating: Creates new row with version++, source_id points to original
 *
 * ## Three-Layer Architecture
 *
 * 1. **WorkflowNodeConfig** - Pure configuration and execution logic (no ID)
 * 2. **WorkflowNodeEntity** (this class) - JPA entity for persistence
 * 3. **ExecutableNode** - Runtime DTO combining entity metadata with config
 *
 * @see WorkflowNodeConfig
 * @see WorkflowNode
 * @see cranium.core.deserializer.WorkflowNodeConfigDeserializer
 */
@Entity
@Table(
    name = "workflow_nodes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_workflow_nodes_workspace_key_version",
            columnNames = ["workspace_id", "key", "version"]
        )
    ],
    indexes = [
        Index(name = "idx_workflow_nodes_workspace_id", columnList = "workspace_id"),
        Index(name = "idx_workflow_nodes_type", columnList = "workspace_id, type"),
        Index(name = "idx_workflow_nodes_source_id", columnList = "workspace_id, source_id"),
        Index(name = "idx_workflow_nodes_key", columnList = "workspace_id, key"),
    ]
)
@SQLRestriction("deleted = false")
data class WorkflowNodeEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "description", nullable = true)
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: WorkflowNodeType,

    @Column(name = "version", nullable = false)
    val version: Int = 1,

    @Column(name = "source_id", columnDefinition = "uuid", nullable = true)
    val sourceId: UUID? = null,

    @Type(JsonBinaryType::class)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    val config: WorkflowNodeConfig,

    @Column(name = "system", nullable = false)
    val system: Boolean = false,

) : AuditableSoftDeletableEntity() {

    /**
     * Converts this entity to an [WorkflowNode] for runtime execution.
     *
     * The ExecutableNode combines entity metadata (id, workspaceId, key, name)
     * with the config's execution logic, providing a complete runtime context.
     *
     * @return An ExecutableNode ready for workflow execution
     * @throws IllegalArgumentException if the entity's id or workspaceId is null
     */
    fun toModel(): WorkflowNode {
        val id = requireNotNull(this.id) { "WorkflowNodeEntity ID cannot be null when converting to ExecutableNode" }


        return WorkflowNode(
            id = id,
            workspaceId = this.workspaceId,
            key = this.key,
            name = this.name,
            description = this.description,
            config = this.config
        )
    }

    companion object {
        /**
         * Creates a WorkflowNodeEntity from a WorkflowNodeConfig.
         *
         * @param workspaceId The workspace this node belongs to
         * @param key Unique identifier for this node within the workspace
         * @param name Human-readable name
         * @param description Optional description
         * @param config The WorkflowNodeConfig (will be serialized to JSONB)
         * @param system Whether this is a system node
         * @return A WorkflowNodeEntity ready to persist
         */
        fun fromConfig(
            workspaceId: UUID,
            key: String,
            name: String,
            description: String?,
            config: WorkflowNodeConfig,
            system: Boolean = false
        ): WorkflowNodeEntity {
            return WorkflowNodeEntity(
                workspaceId = workspaceId,
                key = key,
                name = name,
                description = description,
                type = config.type,
                version = config.version,
                config = config,
                system = system
            )
        }

        /**
         * Creates a new version of an existing node (copy-on-write pattern).
         *
         * @param original The original WorkflowNodeEntity
         * @param updatedConfig The updated WorkflowNodeConfig
         * @return A new WorkflowNodeEntity with incremented version and source_id set
         */
        fun createNewVersion(
            original: WorkflowNodeEntity,
            updatedConfig: WorkflowNodeConfig
        ): WorkflowNodeEntity {
            return original.copy(
                id = null, // New entity, let JPA generate new ID
                version = original.version + 1,
                sourceId = original.id, // Link back to original
                config = updatedConfig,
            ).also {
                it.deleted = false
                it.deletedAt = null
            }
        }
    }
}