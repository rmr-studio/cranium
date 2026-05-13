package cranium.core.models.workflow

import cranium.core.entity.workflow.WorkflowEdgeEntity
import cranium.core.models.workflow.node.WorkflowNode
import java.util.*

data class WorkflowEdge(
    val id: UUID,
    val label: String? = null,
    val source: WorkflowNode,
    val target: WorkflowNode
) {
    companion object Factory {
        fun createEdges(nodes: List<WorkflowNode>, edges: List<WorkflowEdgeEntity>): List<WorkflowEdge> {
            return nodes.associateBy { it.id }.let {
                edges.mapNotNull { edge ->
                    val source = it[edge.sourceNodeId]
                    val target = it[edge.targetNodeId]
                    if (source == null || target == null) return@mapNotNull null
                    edge.toModel(source, target)
                }
            }
        }
    }
}