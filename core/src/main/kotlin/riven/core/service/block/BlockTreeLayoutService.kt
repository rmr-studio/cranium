package cranium.core.service.block

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.entity.block.BlockTreeLayoutEntity
import cranium.core.models.block.layout.TreeLayout
import cranium.core.repository.block.BlockTreeLayoutRepository
import cranium.core.util.ServiceUtil.findOrThrow
import java.util.*


@Service
class BlockTreeLayoutService(
    private val layoutRepository: BlockTreeLayoutRepository,
) {

    fun fetchLayoutById(
        layoutId: UUID
    ): BlockTreeLayoutEntity {
        return findOrThrow { layoutRepository.findById(layoutId) }
    }

    fun fetchLayoutForEntity(
        id: UUID,
    ): BlockTreeLayoutEntity {
        return findOrThrow { layoutRepository.findByEntityId(id) }
    }


    @Transactional
    fun updateLayoutSnapshot(
        prev: BlockTreeLayoutEntity,
        layout: TreeLayout,
        version: Int
    ): BlockTreeLayoutEntity {
        prev.apply {
            this.layout = layout
            this.version = version
        }.run {
            return layoutRepository.save(this)
        }
    }


    /**
     * Extracts all block IDs from a layout, including nested blocks in subGridOpts.
     * This is useful for:
     * - Batch loading blocks
     * - Validating layout integrity
     * - Bulk operations on all blocks in a layout
     *
     * @param treeLayout The BlockTreeLayout to extract IDs from
     * @return Set of all block UUIDs found in the layout structure
     */
    fun extractBlockIdsFromTreeLayout(treeLayout: TreeLayout): Set<UUID> {
        val ids = mutableSetOf<UUID>()

        fun traverseWidgets(widgets: List<cranium.core.models.block.layout.Widget>?) {
            widgets?.forEach { widget ->
                // Extract block ID from widget content
                widget.content?.id?.let { idString ->
                    try {
                        ids.add(UUID.fromString(idString))
                    } catch (e: IllegalArgumentException) {
                        // Skip invalid UUIDs (could be placeholder IDs)
                    }
                }

                // Recursively process nested widgets in subGridOpts
                widget.subGridOpts?.children?.let { nestedChildren ->
                    traverseWidgets(nestedChildren)
                }
            }
        }

        traverseWidgets(treeLayout.children)
        return ids
    }
}


