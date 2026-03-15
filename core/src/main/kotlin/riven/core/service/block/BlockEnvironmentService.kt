package riven.core.service.block

import io.github.oshai.kotlinlogging.KLogger
import jakarta.transaction.Transactional
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import riven.core.entity.activity.ActivityLogEntity
import riven.core.entity.block.BlockEntity
import riven.core.enums.activity.Activity
import riven.core.enums.block.node.BlockReferenceWarning
import riven.core.enums.block.request.BlockOperationType
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.exceptions.NotFoundException
import riven.core.models.block.BlockEnvironment
import riven.core.models.block.layout.TreeLayout
import riven.core.models.block.layout.Widget
import riven.core.models.block.metadata.BlockReferenceMetadata
import riven.core.models.block.metadata.EntityReferenceMetadata
import riven.core.models.block.operation.*
import riven.core.models.block.tree.*
import riven.core.models.request.block.HydrateBlocksRequest
import riven.core.models.request.block.OverwriteEnvironmentRequest
import riven.core.models.request.block.SaveEnvironmentRequest
import riven.core.models.request.block.StructuralOperationRequest
import riven.core.models.response.block.OverwriteEnvironmentResponse
import riven.core.models.response.block.SaveEnvironmentResponse
import riven.core.models.response.block.internal.BlockHydrationResult
import riven.core.service.activity.ActivityService
import riven.core.models.websocket.BlockEnvironmentEvent
import riven.core.service.auth.AuthTokenService
import org.springframework.context.ApplicationEventPublisher
import java.util.*

@Service
class BlockEnvironmentService(
    private val blockService: BlockService,
    private val blockTreeLayoutService: BlockTreeLayoutService,
    private val blockReferenceService: BlockReferenceHydrationService,
    private val blockChildrenService: BlockChildrenService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService,
    private val defaultEnvironmentService: DefaultBlockEnvironmentService,
    private val logger: KLogger,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#request.workspaceId)")
    @Transactional
    fun saveBlockEnvironment(request: SaveEnvironmentRequest): SaveEnvironmentResponse {
        authTokenService.getUserId().let { userId ->
            val layout = blockTreeLayoutService.fetchLayoutById(request.layoutId)
            if (request.version <= layout.version) {
                return SaveEnvironmentResponse(
                    success = false,
                    conflict = true,
                    latestVersion = layout.version,
                    lastModifiedAt = layout.updatedAt,
                    lastModifiedBy = layout.updatedBy?.toString()
                )
            }

            request.operations.map { operation ->
                logBlockOperation(
                    userId = userId,
                    workspaceId = request.workspaceId,
                    operation = operation
                )
            }.run {
                activityService.logActivities(this)
            }

            // 1. Filter out operations for blocks that will be cascade deleted
            val filteredOperations = filterCascadeDeletedOperations(request.operations)

            // 2. Filter and de-duplicate operations => Ensure that only one operation per block per type is processed
            val normalizedOperations: Map<UUID, List<StructuralOperationRequest>> =
                normalizeOperations(filteredOperations)

            // Fetch all involved blocks, references and children ahead of processing
            // Fetch blocks that have operations after normalization
            val blockIds = normalizedOperations.entries.filter { it.value.isNotEmpty() }.map { it.key }.toSet()
            val blocks: Map<UUID, BlockEntity> = blockService.getBlocks(blockIds)

            // 2a. Execute Operations and collect ID mappings
            val allIdMappings = mutableMapOf<UUID, UUID>()

            normalizedOperations.entries.forEach { entry ->
                val (blockId, operations) = entry
                val block: BlockEntity? = blocks[blockId]

                val mappings = executeOperations(
                    operations = operations,
                    block = block,
                    existingIdMappings = allIdMappings
                )

                allIdMappings.putAll(mappings)
            }

            // Save layout snapshot with updated mappings
            val updatedLayout = request.layout.let { layout ->
                layout.children?.forEach { widget ->
                    applyIdMapping(widget, allIdMappings)
                }
                layout
            }

            applicationEventPublisher.publishEvent(
                BlockEnvironmentEvent(
                    workspaceId = request.workspaceId,
                    userId = userId,
                    operation = OperationType.UPDATE,
                    entityId = null,
                    layoutId = request.layoutId,
                    version = request.version,
                    summary = mapOf(
                        "operationCount" to request.operations.size,
                    ),
                )
            )

            blockTreeLayoutService.updateLayoutSnapshot(layout, updatedLayout, request.version).run {
                return SaveEnvironmentResponse(
                    success = true,
                    conflict = false,
                    layout = updatedLayout,
                    newVersion = request.version,
                    latestVersion = request.version,
                    lastModifiedAt = layout.updatedAt,
                    lastModifiedBy = layout.updatedBy?.toString(),
                    idMappings = allIdMappings
                )
            }
        }
    }

    /**
     * Applies ID mappings to a widget and its children recursively.
     * Updates both the widget's main ID and its content ID from temporary IDs to permanent database IDs.
     *
     * @param widget The widget to update
     * @param mapping Map of temporary UUIDs to permanent database UUIDs
     */
    private fun applyIdMapping(
        widget: Widget,
        mapping: Map<UUID, UUID>
    ) {
        // Map widget's main ID if it's a temporary ID
        try {
            val widgetId = UUID.fromString(widget.id)
            mapping[widgetId]?.let { newId ->
                widget.id = newId.toString()
            }
        } catch (e: Exception) {
            // Widget ID is not a valid UUID, skip mapping
            logger.warn { "Widget ${widget.id} is not currently assigned a valid UUID as its primary identifier" }
        }

        // Map content ID if present
        widget.content?.id?.let { contentIdStr ->
            try {
                val contentId = UUID.fromString(contentIdStr)
                mapping[contentId]?.let { newId ->
                    widget.content.id = newId.toString()
                }
            } catch (e: Exception) {
                // Content ID is not a valid UUID, skip mapping
                logger.warn { "Widget ${widget.content.id} is not currently assigned a valid UUID as its primary identifier" }
            }
        }

        // Recursively apply to children in subGridOpts
        widget.subGridOpts?.children?.forEach { childWidget ->
            applyIdMapping(childWidget, mapping)
        }
    }

    private fun logBlockOperation(
        userId: UUID,
        workspaceId: UUID,
        operation: StructuralOperationRequest
    ): ActivityLogEntity {
        val operationData = operation.data // Assign to local variable for smart casting

        return ActivityLogEntity(
            userId = userId,
            workspaceId = workspaceId,
            activity = Activity.BLOCK_OPERATION,
            operation = when (operationData.type) {
                BlockOperationType.ADD_BLOCK -> OperationType.CREATE
                BlockOperationType.REMOVE_BLOCK -> OperationType.DELETE
                else -> OperationType.UPDATE
            },
            entityType = ApplicationEntityType.BLOCK,
            entityId = operationData.blockId,
            timestamp = operation.timestamp,
            details = when (operationData) {
                is AddBlockOperation -> {
                    mapOf(
                        "type" to operationData.type,
                        "blockId" to operationData.blockId.toString(),
                        "parentId" to operationData.parentId.toString(),
                    )
                }

                is RemoveBlockOperation -> {
                    mapOf(
                        "type" to operationData.type,
                        "blockId" to operationData.blockId.toString()
                    )
                }
                // Todo: Calculate readable diffs for updates
                is UpdateBlockOperation -> {
                    mapOf(
                        "type" to operationData.type,
                        "blockId" to operationData.blockId.toString(),
                    )
                }

                is MoveBlockOperation -> {
                    mapOf(
                        "type" to operationData.type,
                        "oldParentId" to operationData.fromParentId,
                        "newParentId" to operationData.toParentId
                    )
                }

                is ReorderBlockOperation -> {
                    mapOf(
                        "type" to operationData.type,
                        "previousIndex" to operationData.fromIndex,
                        "newIndex" to operationData.toIndex
                    )
                }
            }
        )
    }

    /**
     * Handles overwriting the entire block environment in the event of a conflict.
     * Would involve deleting all existing blocks and recreating them from the provided layout.
     * To avoid situations where blocks were deleted in a conflicted version, and would no longer exist.
     */
    fun overwriteBlockEnvironment(request: OverwriteEnvironmentRequest): OverwriteEnvironmentResponse {
        throw NotImplementedError()
    }

    /**
     * Loads the complete block environment for an entity.
     * Implements lazy initialization - creates default environment if none exists.
     *
     * @param entityId The ID of the entity (e.g., client ID)
     * @param entityType The type of entity (e.g., CLIENT, WORKSPACE)
     * @return BlockEnvironment with layout, trees, and entity data
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun loadBlockEnvironment(
        entityId: UUID,
        type: ApplicationEntityType,
        workspaceId: UUID
    ): BlockEnvironment {
        // 1. Try to load existing layout, or create default if it doesn't exist
        val layoutEntity = try {
            blockTreeLayoutService.fetchLayoutForEntity(entityId)
        } catch (e: NotFoundException) {
            defaultEnvironmentService.createDefaultEnvironmentForEntity(
                entityId = entityId,
                entityType = type,
                workspaceId = workspaceId
            )
            // Fetch the newly created layout
            blockTreeLayoutService.fetchLayoutForEntity(entityId)
        }

        require(layoutEntity.workspaceId == workspaceId) {
            "Layout workspace ID does not match requested workspace ID"
        }

        // 2. Build block trees from layout
        val trees = buildBlockTreesFromLayout(layoutEntity.layout)


        // 4. Return complete BlockEnvironment
        return BlockEnvironment(
            layout = layoutEntity.toModel(),
            trees = trees,
        )
    }

    @PreAuthorize("@workspaceSecurity.hasWorkspace(#request.workspaceId)")
    fun hydrateEnvironment(request: HydrateBlocksRequest): Map<UUID, BlockHydrationResult> {
        val (references, workspaceId) = request
        return blockReferenceService.hydrateBlockReferences(references, workspaceId)
    }

    /**
     * Builds block trees from the layout structure (layout-driven approach).
     *
     * This is more efficient than BFS when layout is available because:
     * - Single bulk query for all blocks (instead of recursive queries)
     * - No need to query block_children table (layout has relationships)
     * - Layout already contains the structural hierarchy
     *
     * Note: Falls back to empty list if layout has no widgets.
     */
    private fun buildBlockTreesFromLayout(layout: TreeLayout): List<BlockTree> {

        // If no widgets in layout, return empty (default layouts start empty)
        if (layout.children.isNullOrEmpty()) return emptyList()

        // Extract ALL block IDs from layout (including nested in subGridOpts)
        val blockIds = blockTreeLayoutService.extractBlockIdsFromTreeLayout(layout)
        if (blockIds.isEmpty()) return emptyList()

        // Bulk load all blocks in a single query
        val blocksById = blockService.getBlocks(blockIds)

        // Build trees from top-level widgets using layout structure
        return layout.children.mapNotNull { widget ->
            buildTreeFromWidget(widget, blocksById)
        }
    }


    /**
     * Builds a BlockTree from a widget using pre-loaded blocks.
     * Recursively handles children from subGridOpts (nested layouts).
     *
     * Note: This uses the layout structure for relationships instead of querying block_children.
     */
    private fun buildTreeFromWidget(
        widget: Widget,
        blocksById: Map<UUID, BlockEntity>
    ): BlockTree? {
        val blockId = widget.content?.id?.let {
            try {
                UUID.fromString(it)
            } catch (e: Exception) {
                null
            }
        } ?: return null

        val blockEntity = blocksById[blockId] ?: return null

        // Use BlockService.buildNode to handle all node types (content, reference, entity reference)
        // This delegates to existing BFS logic for building individual nodes
        // But we supply children from layout instead of querying DB
        val rootNode = buildNodeFromLayoutWidget(blockEntity, widget, blocksById)

        return BlockTree(root = rootNode)
    }

    /**
     * Builds a Node from a block entity using layout structure for children.
     * This is a layout-aware variant that doesn't query block_children.
     */
    private fun buildNodeFromLayoutWidget(
        blockEntity: BlockEntity,
        widget: Widget,
        blocksById: Map<UUID, BlockEntity>
    ): Node {
        val block = blockEntity.toModel()

        return when (val meta = block.payload) {
            is BlockReferenceMetadata -> {
                // Handle block references
                // TODO: Integrate with BlockReferenceService when available
                ReferenceNode(
                    block = block,
                    reference = BlockTreeReference(
                        reference = ReferenceItem<BlockTree>(
                            id = block.id,
                            entity = null,
                            warning = BlockReferenceWarning.UNSUPPORTED
                        )
                    )
                )
            }

            is EntityReferenceMetadata -> {
                // Handle entity references with LAZY loading for progressive hydration
                // Entity data is NOT resolved here to keep initial load fast.
                // Frontend calls POST /api/v1/block/environment/hydrate to progressively
                // load entity data for specific blocks as needed.
                ReferenceNode(
                    block = block,
                    reference = EntityReference(
                        reference = emptyList(), // Empty - will be hydrated separately
                    )
                )
            }

            is riven.core.models.block.metadata.BlockContentMetadata -> {
                // Content blocks: children come from layout subGridOpts, not DB!
                val childNodes = widget.subGridOpts?.children?.mapNotNull { childWidget ->
                    buildTreeFromWidget(childWidget, blocksById)?.root
                } ?: emptyList()

                ContentNode(
                    block = block,
                    children = childNodes
                )
            }
        }
    }


    /**
     * Filters out operations for blocks that will be cascade deleted when their parent is removed.
     * This prevents creating/updating blocks that will immediately be deleted.
     * Note: This only filters CASCADE deletions (children of removed blocks), not direct removals.
     * Direct ADD+REMOVE combinations are handled by reduceBlockOperations.
     */
    internal fun filterCascadeDeletedOperations(operations: List<StructuralOperationRequest>): List<StructuralOperationRequest> {
        // 1. Collect only CASCADE deleted blocks (children of blocks being removed)
        // Do NOT include blocks with direct REMOVE operations (those are handled by reduceBlockOperations)
        val cascadeDeletedBlocks = operations
            .filter { it.data.type == BlockOperationType.REMOVE_BLOCK }
            .flatMap { (it.data as RemoveBlockOperation).childrenIds.keys }
            .toSet()

        // 2. Filter out any ADD/UPDATE/MOVE/REORDER operations for cascade deleted blocks
        return operations.filter { op ->
            // Keep all REMOVE operations
            if (op.data.type == BlockOperationType.REMOVE_BLOCK) {
                return@filter true
            }

            // Drop operations for blocks that will be CASCADE deleted
            !cascadeDeletedBlocks.contains(op.data.blockId)
        }
    }

    fun normalizeOperations(operations: List<StructuralOperationRequest>): Map<UUID, List<StructuralOperationRequest>> {

        // 2. Group by blockId
        return operations.groupBy { it.data.blockId }.values.flatMap { blockOperations ->
            reduceBlockOperations(blockOperations).sortedBy { it.timestamp }
        }.groupBy { it.data.blockId }
    }

    fun reduceBlockOperations(ops: List<StructuralOperationRequest>): List<StructuralOperationRequest> {

        // If block is added then removed → skip both
        val hasAdd = ops.any { it.data.type == BlockOperationType.ADD_BLOCK }
        val hasRemove = ops.any { it.data.type == BlockOperationType.REMOVE_BLOCK }

        if (hasAdd && hasRemove) {
            return emptyList()
        }

        // If block is deleted. No need to process other operations
        if (hasRemove) {
            return ops.filter { it.data.type == BlockOperationType.REMOVE_BLOCK }
        }

        // Now: no DELETE in this block’s ops

        // Rule 4a/b: If there is a CREATE, drop ops before it and ensure CREATE is first
        val createOp = ops.firstOrNull { it.data.type == BlockOperationType.ADD_BLOCK }

        // Keep only ops at or after CREATE (given it exists)
        val relevantOps = if (createOp != null) {
            ops.filter { it.timestamp >= createOp.timestamp }
        } else {
            ops
        }

        // From here we enforce "only one operation per block per type"
        // by keeping only the last op per type.
        val lastByType: Map<BlockOperationType, StructuralOperationRequest> =
            relevantOps.groupBy { it.data.type }
                .mapValues { (_, sameTypeOps) ->
                    sameTypeOps.maxByOrNull { it.timestamp }!!
                }

        val result = mutableListOf<StructuralOperationRequest>()

        // If we have CREATE, ensure it's first in the list
        if (createOp != null) {
            val finalCreate = lastByType[BlockOperationType.ADD_BLOCK] ?: createOp
            result.addFirst(finalCreate)
        }

        // Add remaining types (UPDATE / REPARENT / REPOSITION), ordered by timestamp
        val others = lastByType
            .filterKeys { it != BlockOperationType.ADD_BLOCK }
            .values
            .sortedBy { it.timestamp }

        result += others

        return result
    }

    private fun executeOperations(
        operations: List<StructuralOperationRequest>,
        block: BlockEntity? = null,
        existingIdMappings: Map<UUID, UUID> = emptyMap()
    ): Map<UUID, UUID> {
        // PHASE 1: Handle REMOVE operations (will need to cascade delete children)
        val removeOps = operations.filter { it.data.type == BlockOperationType.REMOVE_BLOCK }
        if (removeOps.isNotEmpty()) {
            // Collect all blocks to remove (parents + all children from childrenIds map)
            val blockIdsToRemove = removeOps.flatMap { op ->
                val data = op.data as RemoveBlockOperation
                listOf(data.blockId) + data.childrenIds.keys
            }.toSet()

            // Collect all parent IDs that have children to delete their relationship records
            val parentsToRemove = removeOps.flatMap { op ->
                val data = op.data as RemoveBlockOperation
                if (data.childrenIds.isNotEmpty()) {
                    // Include the parent block and any nested parents (from the values of childrenIds)
                    (listOf(data.blockId) + data.childrenIds.values).toSet()
                } else {
                    emptyList()
                }
            }.toSet()

            // Delete all records of children relationships for these parents
            if (parentsToRemove.isNotEmpty()) {
                blockChildrenService.deleteAllInBatch(parentsToRemove)
            }

            // Batch delete all blocks (parents and children)
            if (blockIdsToRemove.isNotEmpty()) {
                blockService.deleteAllById(blockIdsToRemove)
            }

            return emptyMap() // No ID mappings needed if block is deleted
        }

        // PHASE 2: Handle ADD operations (create blocks + children)
        val addOps = operations.filter { it.data.type == BlockOperationType.ADD_BLOCK }
            .map { it.data as AddBlockOperation }

        val newBlocks = mutableListOf<BlockEntity>()
        val childAdditions: List<AddBlockOperation> = addOps.filter { it.parentId != null }

        addOps.forEach { addOp ->
            val blockData = addOp.block.block
            val blockTypeEntity = blockService.getBlockTypeEntity(blockData.type.id)

            val entity = BlockEntity(
                id = null, // Let DB generate new ID
                workspaceId = blockData.workspaceId,
                type = blockTypeEntity,
                name = blockData.name,
                payload = blockData.payload,
            )

            newBlocks.add(entity)
        }

        // Batch save new blocks
        val savedBlocks = if (newBlocks.isNotEmpty()) {
            blockService.saveAll(newBlocks)
        } else {
            emptyList()
        }

        // Map temporary IDs to real IDs (combine with existing mappings from previous executions)
        val idMapping = mutableMapOf<UUID, UUID>()
        addOps.zip(savedBlocks).forEach { (addOp, savedBlock) ->
            idMapping[addOp.blockId] = savedBlock.id!!
        }

        // Helper function to resolve IDs (temp -> real)
        // Check local mappings first, then existing mappings from previous executions
        val resolveId: (UUID) -> UUID = { id ->
            idMapping[id] ?: existingIdMappings[id] ?: id
        }

        // Update child additions with real IDs (resolve both child and parent)
        val resolvedChildAdditions = childAdditions.map { addition ->
            addition.copy(
                blockId = resolveId(addition.blockId),
                parentId = resolveId(addition.parentId!!) // Also resolve parent in case it was newly added
            )
        }

        // Batch save parent-child relationships
        if (resolvedChildAdditions.isNotEmpty()) {
            val allChildren = blockChildrenService.getChildrenForBlocks(
                resolvedChildAdditions.mapNotNull { it.parentId }
            )
            val childEntities = blockChildrenService.prepareChildAdditions(
                resolvedChildAdditions,
                allChildren
            )
            if (childEntities.isNotEmpty()) {
                blockChildrenService.saveAll(childEntities)
            }
        }

        // PHASE 3: Handle UPDATE operations (modify existing blocks)
        val updateOps = operations.filter { it.data.type == BlockOperationType.UPDATE_BLOCK }
            .map { it.data as UpdateBlockOperation }

        val blocksToUpdate = mutableListOf<BlockEntity>()
        updateOps.forEach { updateOp ->
            // Resolve block ID in case it was newly added
            val resolvedBlockId = resolveId(updateOp.blockId)

            // Try to find in existing blocks first, or in newly saved blocks
            val existingBlock = block?.takeIf { it.id == resolvedBlockId }
                ?: savedBlocks.find { it.id == resolvedBlockId }

            // TODO: Validate content of new block

            existingBlock?.let { existing ->
                val updatedContent = updateOp.updatedContent.block

                // Update payload with new content
                val updated = existing.copy(
                    name = updatedContent.name ?: existing.name,
                    payload = updatedContent.payload
                )
                blocksToUpdate.add(updated)
            }
        }

        // Batch save updated blocks
        if (blocksToUpdate.isNotEmpty()) {
            blockService.saveAll(blocksToUpdate)
        }

        // PHASE 4: Handle MOVE operations (reparent blocks)
        val moveOps = operations.filter { it.data.type == BlockOperationType.MOVE_BLOCK }
            .map { it.data as MoveBlockOperation }

        if (moveOps.isNotEmpty()) {
            val moves = moveOps.map { moveOp ->
                moveOp.copy(
                    blockId = resolveId(moveOp.blockId), // Resolve child ID
                    fromParentId = moveOp.fromParentId?.let { resolveId(it) }, // Resolve old parent ID
                    toParentId = moveOp.toParentId?.let { resolveId(it) } // Resolve new parent ID
                )

            }

            val affectedParents = moves.flatMap {
                listOfNotNull(
                    it.fromParentId?.let { parentId -> resolveId(parentId) },
                    it.toParentId?.let { parentId -> resolveId(parentId) }
                )
            }.toSet()

            val existingChildren = blockChildrenService.getChildrenForBlocks(affectedParents)
            val moveResult = blockChildrenService.prepareChildMoves(moves, existingChildren)

            // Batch delete old edges
            if (moveResult.childEntitiesToDelete.isNotEmpty()) {
                blockChildrenService.deleteAllInBatch(moveResult.childEntitiesToDelete)
            }

            // Batch save new edges
            if (moveResult.childEntitiesToSave.isNotEmpty()) {
                blockChildrenService.saveAll(moveResult.childEntitiesToSave)
            }
        }

        // PHASE 5: Handle REORDER operations (change indices within parent)
        val reorderOps = operations.filter { it.data.type == BlockOperationType.REORDER_BLOCK }
            .map { it.data as ReorderBlockOperation }

        if (reorderOps.isNotEmpty()) {
            val reorders = reorderOps.map { reorderOp ->
                reorderOp.copy(
                    blockId = resolveId(reorderOp.blockId), // Resolve child ID
                    parentId = resolveId(reorderOp.parentId) // Resolve parent ID
                )
            }

            val affectedParents = reorders.map { resolveId(it.parentId) }.toSet()
            val existingChildren = blockChildrenService.getChildrenForBlocks(affectedParents)
            val reorderedEntities = blockChildrenService.prepareChildReorders(reorders, existingChildren)

            // Batch save reordered edges
            if (reorderedEntities.isNotEmpty()) {
                blockChildrenService.saveAll(reorderedEntities)
            }
        }

        return idMapping
    }


}