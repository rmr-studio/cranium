package riven.core.models.request.identity

import java.util.UUID

/**
 * Request body for manually adding an entity to an identity cluster.
 *
 * @param entityId The entity to add to the cluster.
 * @param targetMemberId An existing member of the cluster to create a CONNECTED_ENTITIES relationship with.
 */
data class AddClusterMemberRequest(
    val entityId: UUID,
    val targetMemberId: UUID,
)
