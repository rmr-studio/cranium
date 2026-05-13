package cranium.core.entity.block

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import cranium.core.entity.util.AuditableSoftDeletableEntity
import cranium.core.enums.common.validation.ValidationScope
import cranium.core.models.block.BlockType
import cranium.core.models.block.BlockTypeSchema
import cranium.core.models.block.display.BlockDisplay
import cranium.core.models.block.display.BlockTypeNesting
import cranium.core.models.request.block.CreateBlockTypeRequest
import java.util.*

/**
 * Defines a type of block that can be used within the system. This will hold all information
 * about
 *  - The data structure of the block
 *  - The format for data input
 *  - The components and display structure when rendering the block
 */
@Entity
@Table(
    name = "block_types",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["workspace_id", "key"])
    ],
    indexes = [
        Index(name = "idx_block_types_workspace_id", columnList = "workspace_id")
    ]
)
@SQLRestriction("deleted = false")
data class BlockTypeEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "key", nullable = false)
    val key: String,

    @Column(name = "source_id", columnDefinition = "uuid", nullable = true)
    val sourceId: UUID? = null,

    @Column(name = "display_name", nullable = false)
    val displayName: String,

    @Column(name = "description", nullable = true)
    val description: String? = null,

    @Column(name = "workspace_id", columnDefinition = "uuid")
    val workspaceId: UUID? = null,

    @Column(name = "system", nullable = false)
    val system: Boolean = false,

    @Column(name = "version", nullable = false, columnDefinition = "integer default 1")
    val version: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column(name = "strictness", nullable = false, columnDefinition = "text default 'SOFT'")
    val strictness: ValidationScope = ValidationScope.SOFT,

    @Column(name = "schema", columnDefinition = "jsonb", nullable = false)
    @Type(JsonBinaryType::class)
    val schema: BlockTypeSchema,

    @Column(name = "nesting", columnDefinition = "jsonb", nullable = true)
    @Type(JsonBinaryType::class)
    val nesting: BlockTypeNesting? = null,

    @Column(name = "display_structure", columnDefinition = "jsonb", nullable = false)
    @Type(JsonBinaryType::class)
    val displayStructure: BlockDisplay,
) : AuditableSoftDeletableEntity() {
    /**
     * Convert this entity into a domain BlockType model.
     *
     * @return A BlockType populated with the entity's identifier, metadata, schema, display structure, audit fields, and related properties.
     * @throws IllegalArgumentException if the entity's `id` is null.
     */
    fun toModel(): BlockType {
        val id = requireNotNull(this.id) { "BlockTypeEntity ID cannot be null when converting to model" }

        return BlockType(
            id = id,
            key = this.key,
            version = this.version,
            name = this.displayName,
            description = this.description,
            workspaceId = this.workspaceId,
            system = this.system,
            schema = this.schema,
            deleted = this.deleted,
            strictness = this.strictness,
            nesting = this.nesting,
            display = this.displayStructure,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            createdBy = this.createdBy,
            updatedBy = this.updatedBy,
            sourceId = this.sourceId,
        )
    }

    companion object {
        /**
         * Creates a BlockTypeEntity from a CreateBlockTypeRequest.
         *
         * Populates the entity's key, displayName, description, workspaceId, strictness, schema, and displayStructure from the request and ensures `system` is set to `false` (system block types cannot be created via this method).
         *
         * @param request The create request whose fields are used to populate the entity.
         * @return A BlockTypeEntity populated from the request with `system` set to `false`.
         */
        fun fromRequest(request: CreateBlockTypeRequest): BlockTypeEntity {
            return BlockTypeEntity(
                key = request.key,
                displayName = request.name,
                description = request.description,
                // Workspace should only be null for system types
                workspaceId = request.workspaceId,
                // System block types cannot be created via this method
                system = false,
                strictness = request.mode,
                schema = request.schema,
                displayStructure = request.display,
            )
        }

        fun fromModel(model: BlockType): BlockTypeEntity {
            return BlockTypeEntity(
                id = model.id,
                key = model.key,
                sourceId = model.sourceId,
                displayName = model.name,
                description = model.description,
                workspaceId = model.workspaceId,
                system = model.system,
                version = model.version,
                strictness = model.strictness,
                schema = model.schema,
                nesting = model.nesting,
                displayStructure = model.display,
            ).also {
                it.deleted = model.deleted
            }
        }
    }
}

