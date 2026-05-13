package cranium.core.models.request.workflow

import java.util.UUID

/**
 * Request model for creating a new workflow edge.
 *
 * @property sourceNodeId The ID of the source node (edge origin)
 * @property targetNodeId The ID of the target node (edge destination)
 * @property label Optional label for the edge (e.g., condition name)
 */
data class CreateWorkflowEdgeRequest(
    val sourceNodeId: UUID,
    val targetNodeId: UUID,
    val label: String? = null
)
