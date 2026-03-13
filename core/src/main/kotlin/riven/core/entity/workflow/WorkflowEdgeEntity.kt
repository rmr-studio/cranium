package riven.core.entity.workflow

import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import riven.core.entity.util.AuditableSoftDeletableEntity
import riven.core.models.workflow.WorkflowEdge
import riven.core.models.workflow.node.WorkflowNode
import java.util.*

@Entity
@Table(
    name = "workflow_edges",
    indexes = [
        Index(name = "idx_workflow_edges_source_node_id", columnList = "workspace_id, source_node_id"),
        Index(name = "idx_workflow_edges_target_node_id", columnList = "workspace_id, target_node_id"),
    ]
)
@SQLRestriction("deleted = false")
data class WorkflowEdgeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    val id: UUID? = null,

    @Column(name = "workspace_id", nullable = false)
    val workspaceId: UUID,

    @Column(name = "source_node_id", nullable = false, columnDefinition = "uuid")
    val sourceNodeId: UUID,

    @Column(name = "target_node_id", nullable = false, columnDefinition = "uuid")
    val targetNodeId: UUID,

    @Column(name = "label", nullable = true)
    val label: String? = null,
) : AuditableSoftDeletableEntity() {
    fun toModel(source: WorkflowNode, target: WorkflowNode): WorkflowEdge {
        val id = requireNotNull(this.id) { "WorkflowEdgeEntity id cannot be null when converting to model." }
        return WorkflowEdge(
            id = id,
            label = this.label,
            source = source,
            target = target
        )
    }
}