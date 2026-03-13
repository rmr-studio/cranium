package riven.core.entity.block

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.hibernate.annotations.Type
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.models.block.layout.TreeLayout
import riven.core.models.block.tree.BlockTreeLayout
import java.util.*


@Entity
@Table(
    name = "block_tree_layouts",
    indexes = [
        Index(name = "idx_block_tree_layouts_entity_id", columnList = "entity_id"),
        Index(name = "idx_block_tree_layouts_workspace_id", columnList = "workspace_id"),
    ]
)
@SQLRestriction("deleted = false")
data class BlockTreeLayoutEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    /**
     * The page (of blocks) this layout applies to
     */
    @Column(name = "entity_id", nullable = false, columnDefinition = "uuid")
    val entityId: UUID,

    @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
    val workspaceId: UUID,

    @Column(name = "version", nullable = false)
    var version: Int = 1,

    /**
     * The complete Gridstack layout configuration
     * Stores all positioning, dimensions, grid options, and nested sub-grids
     */
    @Type(JsonBinaryType::class)
    @Column(name = "layout", columnDefinition = "jsonb", nullable = false)
    var layout: TreeLayout,
) : AuditableSoftDeletableEntity() {
    fun toModel(audit: Boolean = false): BlockTreeLayout {
        val id = requireNotNull(this.id) { "BlockTreeLayoutEntity ID cannot be null when converting to model" }
        return BlockTreeLayout(
            id = id,
            workspaceId = this.workspaceId,
            layout = this.layout,
            version = this.version,
            createdAt = if (audit) this.createdAt else null,
            updatedAt = if (audit) this.updatedAt else null,
            createdBy = if (audit) this.createdBy else null,
            updatedBy = if (audit) this.updatedBy else null,
        )
    }

}