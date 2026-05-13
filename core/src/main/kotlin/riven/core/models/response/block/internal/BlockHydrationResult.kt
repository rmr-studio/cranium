package cranium.core.models.response.block.internal

import cranium.core.models.block.tree.ReferencePayload
import java.util.*

/**
 * Result of hydrating a single block's entity references.
 *
 * @param blockId The UUID of the block that was hydrated.
 * @param references The resolved entity references for this block.
 * @param error Optional error message if hydration failed for this block.
 */
data class BlockHydrationResult(
    val blockId: UUID,
    val references: List<ReferencePayload>,
    val error: String? = null
)

