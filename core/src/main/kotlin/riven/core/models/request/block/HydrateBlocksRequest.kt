package cranium.core.models.request.block

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import cranium.core.enums.block.node.ReferenceType
import java.util.*

/**
 * Request to hydrate (resolve entity references for) one or more blocks.
 *
 * This is used to progressively load entity data for reference blocks
 * without fetching everything upfront during initial environment load.
 *
 * @param references A map where each key is a block ID and the value is a list of entity references associated to that block
 * @param workspaceId The workspace context for authorization and filtering.
 */
data class HydrateBlocksRequest(
    @field:NotEmpty(message = "block references must not be empty")
    val references: Map<UUID, List<EntityReferenceRequest>>,
    @field:NotNull(message = "workspaceId is required")
    var workspaceId: UUID
)

data class EntityReferenceRequest(
    val id: UUID,
    val type: ReferenceType,
    val index: Int? = null
)


