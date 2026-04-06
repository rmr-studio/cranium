package riven.core.service.block

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.block.BlockChildEntity
import riven.core.entity.block.BlockEntity
import riven.core.entity.block.BlockTypeEntity
import riven.core.enums.common.validation.ValidationScope
import riven.core.enums.core.ApplicationEntityType
import riven.core.enums.util.OperationType
import riven.core.exceptions.SchemaValidationException
import riven.core.models.block.Block
import riven.core.models.block.metadata.*
import riven.core.models.block.tree.*
import riven.core.models.common.json.JsonObject
import riven.core.models.request.block.CreateBlockRequest
import riven.core.repository.block.BlockRepository
import riven.core.service.activity.ActivityService
import riven.core.service.activity.log
import riven.core.service.auth.AuthTokenService
import riven.core.service.schema.SchemaService
import java.util.*

/**
 * Service layer for managing blocks within the application.
 */
@Service
class BlockService(
    private val blockRepository: BlockRepository,
    private val blockTypeService: BlockTypeService,
    private val blockChildrenService: BlockChildrenService,
    private val schemaService: SchemaService,
    private val authTokenService: AuthTokenService,
    private val activityService: ActivityService
) {
    // ---------- CREATE ----------
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun createBlock(workspaceId: UUID, request: CreateBlockRequest): BlockEntity {
        val (type, payload, name, parentId, index) = request
        require(!type.deleted) { "BlockType '${type.deleted}' is deleted" }

        // 1) Validate ONLY content blocks
        val validatedMetadata: Metadata =
            when (payload) {
                is BlockContentMetadata -> {
                    val errs = schemaService.validate(type.schema, payload.data, type.strictness)
                    if (type.strictness == ValidationScope.STRICT && errs.isNotEmpty()) {
                        throw SchemaValidationException(errs)
                    }

                    payload.meta.apply {
                        this.lastValidatedVersion = type.version
                        this.validationErrors = errs
                    }

                    payload
                }

                is ReferenceMetadata -> {
                    payload.meta.apply {
                        this.lastValidatedVersion = type.version
                    }
                    payload
                }
            }

        return BlockEntity(
            id = null,
            workspaceId = workspaceId,
            type = BlockTypeEntity.fromModel(type),
            name = name,
            payload = validatedMetadata,
        ).run {
            blockRepository.save(this)
        }.also {
            requireNotNull(it.id) { "Block '${it.id}' not found" }
            activityService.log(
                activity = riven.core.enums.activity.Activity.BLOCK,
                operation = OperationType.CREATE,
                userId = authTokenService.getUserId(),
                workspaceId = workspaceId,
                entityType = ApplicationEntityType.BLOCK,
                entityId = it.id,
                "blockId" to it.id.toString(),
                "typeKey" to type.key
            )

            // If a parent ID is supplied. This would indicate we are creating a child block
            parentId?.let { parentId ->
                getBlock(parentId).also { parent ->
                    require(parent.payload is BlockContentMetadata) {
                        "Parent block '${parent.id}' is not a content block and cannot have children"
                    }

                    // This will make a DB call to fetch the type of the parent block.
                    // I wonder if i need this given we have frontend validation...

                    val nesting = requireNotNull(parent.type.nesting) {
                        "Parent block type '${parent.type.key}' does not support nesting of child blocks"
                    }

                    // Validate and add block as a child of provided parent
                    blockChildrenService.addChild(
                        child = it,
                        parentId = parentId,
                        index = index,
                        nesting = nesting
                    )
                }
            }
        }
    }


    // ---------- UPDATE ----------
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#block.workspaceId)")
    @Transactional
    fun updateBlock(block: Block): Block {
        val existing = blockRepository.findById(block.id).orElseThrow()
        require(existing.workspaceId == block.workspaceId) {
            "Block does not belong to the specified workspace"
        }

        require(existing.payload::class == block.payload::class) {
            "Cannot switch payload kind (content <-> reference) on update. Create a new block instead."
        }

        require(existing.type.id == block.type.id) {
            "Cannot change block type on update. Create a new block instead."
        }

        val updatedMetadata: Metadata = block.payload.let {
            when (it) {
                is BlockContentMetadata -> {
                    existing.payload.run {
                        require(this is BlockContentMetadata) {
                            "Existing block payload is not BlockContentMetadata"
                        }
                        val updated: JsonObject = deepMergeJson(this.data, it.data)
                        it.apply {
                            this.data = updated
                        }

                        val errs = schemaService.validate(existing.type.schema, it.data, existing.type.strictness)

                        if (existing.type.strictness == ValidationScope.STRICT && errs.isNotEmpty()) {
                            throw SchemaValidationException(errs)
                        }

                        it.meta.apply {
                            this.lastValidatedVersion = block.type.version
                            this.validationErrors = errs
                        }

                        it
                    }


                }

                is ReferenceMetadata -> {
                    it.meta.apply {
                        this.lastValidatedVersion = block.type.version
                    }
                    it
                }
            }
        }

        val updated = existing.apply {
            name = block.name ?: existing.name
            payload = updatedMetadata
        }

        val saved = blockRepository.save(updated)

        activityService.log(
            activity = riven.core.enums.activity.Activity.BLOCK,
            operation = OperationType.UPDATE,
            userId = authTokenService.getUserId(),
            workspaceId = saved.workspaceId,
            entityType = ApplicationEntityType.BLOCK,
            entityId = saved.id,
            "blockId" to saved.id.toString(),
            "typeKey" to saved.type.key
        )

        return saved.toModel()
    }


    // ---------- READ ----------
    fun getBlock(blockId: UUID): BlockEntity {
        return blockRepository.findById(blockId).orElseThrow()
    }

    fun getBlocks(blockIds: Set<UUID>): Map<UUID, BlockEntity> {
        return blockRepository.findAllById(blockIds)
            .mapNotNull { block ->
                val id = block.id ?: return@mapNotNull null
                id to block
            }
            .toMap()
    }

    fun getBlockTree(blockId: UUID): BlockTree {
        val root = blockRepository.findById(blockId).orElseThrow()
        val node = buildNode(root.toModel(), visited = mutableSetOf())
        return BlockTree(
            root = node
        )
    }

    private fun buildNode(block: Block, visited: MutableSet<UUID>): Node {
        if (!visited.add(block.id)) {
            // Cycles only possible for content nodes (ownership graph)
            return ContentNode(block = block, warnings = listOf("Cycle detected at ${block.id}"))
        }

        return when (block.payload) {
            is BlockReferenceMetadata -> {
                visited.remove(block.id)
                ReferenceNode(
                    block = block,
                    reference = BlockTreeReference()
                )
            }

            is EntityReferenceMetadata -> {
                visited.remove(block.id)
                ReferenceNode(
                    block = block,
                    reference = EntityReference(

                    )
                )
            }

            is BlockContentMetadata -> {
                // Pull owned children via BlockChildrenService
                val edges: List<BlockChildEntity> = blockChildrenService.listChildren(block.id)
                val childNodes: List<Node> = if (edges.isNotEmpty()) {
                    // batch fetch children
                    val ids = edges.map { it.childId }.toSet()
                    val childrenById = blockRepository.findAllById(ids).associateBy { it.id!! }
                    edges.sortedBy { it.orderIndex }.mapNotNull { link ->
                        val child = childrenById[link.childId] ?: return@mapNotNull null
                        buildNode(child.toModel(), visited)
                    }
                } else {
                    emptyList()
                }
                visited.remove(block.id)
                ContentNode(block = block, children = childNodes)
            }
        }
    }

    /**
     * Batch save blocks - used for efficient bulk operations.
     */
    fun saveAll(blocks: List<BlockEntity>): List<BlockEntity> {
        return blockRepository.saveAll(blocks).toList()
    }

    /**
     * Batch delete blocks by IDs.
     */
    fun deleteAllById(blockIds: Set<UUID>) {
        blockRepository.deleteAllById(blockIds)
    }

    /**
     * Get BlockTypeEntity by ID - helper for ADD operations.
     */
    fun getBlockTypeEntity(typeId: UUID): BlockTypeEntity {
        return blockTypeService.getById(typeId)
    }

    // ---------- helpers ----------
    @Suppress("UNCHECKED_CAST")
    private fun deepMergeJson(a: Map<String, Any?>, b: Map<String, Any?>): Map<String, Any?> {
        val out = a.toMutableMap()
        for ((k, vb) in b) {
            val va = out[k]
            out[k] = if (va is Map<*, *> && vb is Map<*, *>) {
                deepMergeJson(va as Map<String, Any?>, vb as Map<String, Any?>)
            } else vb
        }
        return out
    }

}