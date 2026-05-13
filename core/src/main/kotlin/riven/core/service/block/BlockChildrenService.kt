package cranium.core.service.block

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import cranium.core.entity.block.BlockChildEntity
import cranium.core.entity.block.BlockEntity
import cranium.core.models.block.display.BlockTypeNesting
import cranium.core.models.block.metadata.BlockContentMetadata
import cranium.core.models.block.operation.AddBlockOperation
import cranium.core.models.block.operation.MoveBlockOperation
import cranium.core.models.block.operation.ReorderBlockOperation
import cranium.core.models.response.block.internal.CascadeRemovalResult
import cranium.core.models.response.block.internal.MovePreparationResult
import cranium.core.repository.block.BlockChildrenRepository
import cranium.core.repository.block.BlockRepository
import java.util.*

/**
 * Manages *owned* parent→child block edges with slots & order.
 *
 * - Enforces: same-workspace, allowed types (via parent.type.nesting),
 *   max children constraint, contiguous orderIndex per slot.
 * - Uses BlockChildEntity as the single source of truth for hierarchy.
 * - Constraint: child_id is globally unique - a child can only belong to ONE parent.
 * - Optionally mirrors parentId on child BlockEntity for denormalization/query performance.
 */
@Service
class BlockChildrenService(
    private val edgeRepository: BlockChildrenRepository,
    private val blockRepository: BlockRepository
) {

    /* =========================
     * Public read operations
     * ========================= */

    /** Returns the ordered list of children */
    fun listChildren(parentId: UUID): List<BlockChildEntity> =
        edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)

    /** Returns a map of parentId to their ordered list of children */
    fun getChildrenForBlocks(blockIds: Collection<UUID>): Map<UUID, List<BlockChildEntity>> {
        val edges = edgeRepository.findByParentIdInOrderByParentIdAndOrderIndex(blockIds)
        return edges.groupBy { it.parentId }
    }

    /* =========================
     * Helpers / Validation
     * ========================= */

    private fun load(id: UUID): BlockEntity =
        blockRepository.findById(id).orElseThrow { NoSuchElementException("Block $id not found") }

    /* =========================
     * Mutations
     * ========================= */

    @Transactional
    fun addChild(
        child: BlockEntity,
        parentId: UUID,
        index: Int? = null,
        nesting: BlockTypeNesting
    ): BlockChildEntity {
        val childId = requireNotNull(child.id)

        val parent = load(parentId)
        validateAttach(parent, child, nesting)

        // Ensure this block is not already a child elsewhere (child_id is globally unique)
        edgeRepository.findByChildId(childId)?.let {
            throw IllegalStateException("Block $childId already exists as a child of parent ${it.parentId}")
        }

        val siblings = edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)
        val insertAt = index?.let {
            index.coerceIn(0, siblings.size)
        }?.also {
            // Shift down indexes >= insertAt
            siblings.asReversed().forEach { s ->
                val currentIndex = s.orderIndex ?: 0
                if (currentIndex >= it) {
                    edgeRepository.save(s.copy(orderIndex = currentIndex + 1))
                }
            }
        }

        val created = edgeRepository.save(
            BlockChildEntity(
                id = null,
                parentId = parentId,
                childId = childId,
                orderIndex = insertAt
            )
        )

        return created
    }

    /**
     * Move a child to a new absolute index within the same parent. This is only accessible to `LIST` types.
     */
    @Transactional
    fun reorderChildren(parentId: UUID, childId: UUID, newIndex: Int) {
        val siblings = edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)
        val row = siblings.find { it.childId == childId }
            ?: throw NoSuchElementException("Child $childId not found in parent $parentId")

        val bounded = newIndex.coerceIn(0, siblings.size - 1)
        if (row.orderIndex == bounded) return

        // Remove and reinsert with compacted indices
        val reordered = siblings
            .filter { it.childId != childId }
            .toMutableList()

        reordered.add(bounded, row.copy(orderIndex = bounded))
        renumber(parentId, reordered)
    }


    /**
     * Reparent a child under a new parent. This is only accessible to `LAYOUT_CONTAINER` types.
     * - Validates org, allowed types, max children.
     * - Updates child's parent pointer if maintained.
     */
    @Transactional
    fun reparentChild(
        childId: UUID,
        newParentId: UUID,
        nesting: BlockTypeNesting
    ) {
        val child = load(childId)
        val newParent = load(newParentId)
        validateAttach(newParent, child, nesting)

        // Remove any existing edge (child_id is globally unique, so at most one edge exists)
        edgeRepository.findByChildId(childId)?.let { existingEdge ->
            edgeRepository.delete(existingEdge)
            // Compact old parent's slot
            val oldSiblings = edgeRepository.findByParentIdOrderByOrderIndexAsc(
                existingEdge.parentId,
            )
            renumber(existingEdge.parentId, oldSiblings)
        }

        edgeRepository.save(
            BlockChildEntity(
                id = null,
                parentId = newParentId,
                childId = childId,
            )
        )
    }

    /**
     * Detach a child from its parent (if any). Does not delete the child block.
     */
    @Transactional
    fun detachChild(childId: UUID) {
        edgeRepository.findByChildId(childId)?.let { edge ->
            edgeRepository.delete(edge)
            // Compact the parent's slot
            val siblings = edgeRepository.findByParentIdOrderByOrderIndexAsc(edge.parentId)
            renumber(edge.parentId, siblings)
        }
    }

    /**
     * Remove a specific child from a specific parent slot.
     */
    @Transactional
    fun removeChild(parentId: UUID, childId: UUID) {
        val row = edgeRepository.findByParentIdAndChildId(parentId, childId)
            ?: return

        edgeRepository.delete(row)
        // compact the slot
        val remaining = edgeRepository.findByParentIdOrderByOrderIndexAsc(parentId)
        renumber(parentId, remaining)
    }


    /**
     * Validates org, nesting rules, and max children constraint.
     * Since child_id is globally unique, cycles are impossible in a strict tree.
     */
    private fun validateAttach(parent: BlockEntity, child: BlockEntity, nesting: BlockTypeNesting) {
        // 1. Same workspace check
        require(parent.workspaceId == child.workspaceId) {
            "Cannot attach child from different workspace (parent: ${parent.workspaceId}, child: ${child.workspaceId})"
        }

        require(parent.payload is BlockContentMetadata) {
            "Cannot attach children to a reference block (parent: ${parent.id})"
        }

        val parentId = requireNotNull(parent.id)
        requireNotNull(child.id)

        // 2. Nesting rules: check if child's type is allowed

        require(nesting.allowedTypes.any { it == child.type.key }) {
            "Child type '$child.type.key'  is not allowed in parent's nesting rules. Allowed types: ${nesting.allowedTypes.joinToString()}}"
        }

        // 3. Max children constraint
        nesting.max?.let { maxChildren ->
            val currentCount = edgeRepository.countByParentId(parentId)
            require(currentCount < maxChildren) {
                "Parent block ${parent.id} has reached maximum children ($maxChildren)"
            }
        }

        // Note: Cycle detection is not needed since child_id is globally unique -
        // a child can only belong to ONE parent, making cycles impossible in a strict tree
    }

    /** Persist contiguous orderIndex = 0..n-1 for provided rows (already filtered to a slot). */
    private fun renumber(parentId: UUID, rows: List<BlockChildEntity>) {
        rows.forEachIndexed { idx, r ->
            if (r.orderIndex != idx) edgeRepository.save(r.copy(orderIndex = idx))
        }
    }

    /* =========================
     * Batch Preparation Methods
     * ========================= */

    /**
     * Recursively collects all descendant block IDs for cascade deletion.
     * Returns a set of all block IDs that should be deleted (including the root blocks).
     */
    fun prepareRemovalCascade(blockIds: Set<UUID>): CascadeRemovalResult {
        val toDelete = mutableSetOf<UUID>()
        val queue = ArrayDeque(blockIds)
        val visited = mutableSetOf<UUID>()

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            if (!visited.add(currentId)) continue // Skip if already processed (cycle protection)

            toDelete.add(currentId)

            // Find all children of this block
            val children = edgeRepository.findByParentIdOrderByOrderIndexAsc(currentId)
            children.forEach { edge ->
                queue.add(edge.childId)
            }
        }

        // Collect all BlockChildEntity records to delete:
        // 1. Where these blocks are parents (already handled by finding children above)
        // 2. Where these blocks are children (edges pointing to them)
        val childEntitiesToDelete = mutableListOf<BlockChildEntity>()

        // Find all edges where blocks in toDelete are children
        toDelete.forEach { blockId ->
            edgeRepository.findByChildId(blockId)?.let { edge ->
                childEntitiesToDelete.add(edge)
            }
        }

        // Find all edges where blocks in toDelete are parents
        val childrenOfDeleted = edgeRepository.findByParentIdInOrderByParentIdAndOrderIndex(toDelete)
        childEntitiesToDelete.addAll(childrenOfDeleted)

        return CascadeRemovalResult(
            blocksToDelete = toDelete,
            childEntitiesToDelete = childEntitiesToDelete.distinctBy { it.id }
        )
    }

    /**
     * Prepares BlockChildEntity records for blocks being added with parents.
     * Handles both List blocks (with numerical indices) and layout containers (without).
     */
    fun prepareChildAdditions(
        additions: List<AddBlockOperation>,
        existingChildren: Map<UUID, List<BlockChildEntity>>
    ): List<BlockChildEntity> {
        val toSave = mutableListOf<BlockChildEntity>()

        // Group additions by parent for efficient index management
        val additionsByParent: Map<UUID, List<AddBlockOperation>> =
            additions.filter { it.parentId != null }.groupBy { it.parentId!! }

        additionsByParent.forEach { (parentId, childAdditions) ->
            val siblings = existingChildren[parentId]?.toMutableList() ?: mutableListOf()

            childAdditions.forEach { addition ->
                val insertAt = when (addition.index) {
                    null -> null // No index for layout containers - let them append without order
                    else -> addition.index.coerceIn(0, siblings.size)
                }

                val newEdge = BlockChildEntity(
                    id = null,
                    parentId = parentId,
                    childId = addition.blockId,
                    orderIndex = insertAt
                )

                if (insertAt != null) {
                    // For List blocks, shift siblings and maintain order
                    siblings.add(insertAt, newEdge)
                } else {
                    // For layout containers, just add without shifting
                    siblings.add(newEdge)
                }
            }

            // Renumber if any additions had indices (List blocks)
            if (childAdditions.any { it.index != null }) {
                siblings.forEachIndexed { idx, edge ->
                    toSave.add(edge.copy(orderIndex = idx))
                }
            } else {
                // For layout containers, just add the new edges
                toSave.addAll(siblings.filter { it.id == null })
            }
        }

        return toSave
    }

    /**
     * Prepares move operations: removes old parent-child edges and creates new ones.
     * Returns entities to delete and entities to save.
     */
    fun prepareChildMoves(
        operations: List<MoveBlockOperation>,
        existingChildren: Map<UUID, List<BlockChildEntity>>
    ): MovePreparationResult {
        val toDelete = mutableListOf<BlockChildEntity>()
        val toSave = mutableListOf<BlockChildEntity>()

        // Track modified parents for renumbering
        val affectedOldParents = mutableMapOf<UUID, MutableList<BlockChildEntity>>()
        val affectedNewParents = mutableMapOf<UUID, MutableList<BlockChildEntity>>()

        operations.forEach { move ->
            // Find and mark old edge for deletion by searching all existing children
            // This is more robust than relying on fromParentId, which may be null or incorrect
            val oldEdge = existingChildren.values.flatten().find { it.childId == move.blockId }

            oldEdge?.let { edge ->
                toDelete.add(edge)

                // Track siblings in old parent for renumbering
                if (!affectedOldParents.containsKey(edge.parentId)) {
                    affectedOldParents[edge.parentId] = existingChildren[edge.parentId]
                        ?.filter { it.childId != move.blockId }
                        ?.toMutableList() ?: mutableListOf()
                }
            }

            // Create new edge for new parent
            move.toParentId?.let { newParentId ->
                val newSiblings = affectedNewParents.getOrPut(newParentId) {
                    existingChildren[newParentId]?.toMutableList() ?: mutableListOf()
                }

                val newEdge = BlockChildEntity(
                    id = null,
                    parentId = newParentId,
                    childId = move.blockId,
                )

                newSiblings.add(newEdge)
                toSave.add(newEdge)
            }
        }

        return MovePreparationResult(
            childEntitiesToDelete = toDelete,
            childEntitiesToSave = toSave
        )
    }

    /**
     * Prepares reorder operations: updates order indices within the same parent.
     * Returns entities to save with updated indices.
     */
    fun prepareChildReorders(
        reorders: List<ReorderBlockOperation>,
        existingChildren: Map<UUID, List<BlockChildEntity>>
    ): List<BlockChildEntity> {
        val toSave = mutableListOf<BlockChildEntity>()

        // Group by parent for efficient processing
        val reordersByParent = reorders.groupBy { it.parentId }

        reordersByParent.forEach { (parentId, parentReorders) ->
            val siblings = existingChildren[parentId]?.toMutableList() ?: return@forEach

            parentReorders.forEach { reorder ->
                val currentIndex = siblings.indexOfFirst { it.childId == reorder.blockId }
                if (currentIndex == -1) return@forEach

                val boundedNewIndex = reorder.toIndex.coerceIn(0, siblings.size - 1)
                if (currentIndex == boundedNewIndex) return@forEach

                // Remove and reinsert
                val edge = siblings.removeAt(currentIndex)
                siblings.add(boundedNewIndex, edge)
            }

            // Renumber all siblings in this parent
            siblings.forEachIndexed { idx, edge ->
                toSave.add(edge.copy(orderIndex = idx))
            }
        }

        return toSave
    }

    /* =========================
     * Batch persistence operations
     * ========================= */

    /**
     * Batch delete child entities without individual SELECT queries.
     */
    fun deleteAllInBatch(entities: List<BlockChildEntity>) {
        edgeRepository.deleteAllInBatch(entities)
    }

    fun deleteAllInBatch(entities: Collection<UUID>) {
        edgeRepository.deleteAllByParentIdIn(entities)
    }

    /**
     * Batch save child entities.
     */
    fun saveAll(entities: List<BlockChildEntity>): List<BlockChildEntity> {
        return edgeRepository.saveAll(entities).toList()
    }
}

