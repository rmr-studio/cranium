package cranium.core.service.util.factory.block

import cranium.core.entity.block.BlockChildEntity
import cranium.core.entity.block.BlockEntity
import cranium.core.entity.block.BlockTreeLayoutEntity
import cranium.core.entity.block.BlockTypeEntity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.common.validation.ValidationScope
import cranium.core.enums.core.ComponentType
import cranium.core.models.block.Block
import cranium.core.models.block.BlockType
import cranium.core.models.block.display.BlockComponentNode
import cranium.core.models.block.display.BlockDisplay
import cranium.core.models.block.display.BlockRenderStructure
import cranium.core.models.block.display.BlockTypeNesting
import cranium.core.models.block.layout.TreeLayout
import cranium.core.models.block.metadata.BlockContentMetadata
import cranium.core.models.block.metadata.BlockMeta
import cranium.core.models.block.operation.*
import cranium.core.models.block.tree.ContentNode
import cranium.core.models.block.tree.Node
import cranium.core.models.common.grid.GridRect
import cranium.core.models.common.grid.LayoutGrid
import cranium.core.models.common.structure.FormStructure
import cranium.core.models.common.validation.Schema
import cranium.core.models.request.block.SaveEnvironmentRequest
import cranium.core.models.request.block.StructuralOperationRequest
import java.time.ZonedDateTime
import java.util.*

object BlockFactory {

    fun createComponent(): BlockComponentNode = BlockComponentNode(
        id = "component_1",
        type = ComponentType.CONTACT_CARD,
        props = mapOf(
            "title" to "Contact Card",
            "showEmail" to true
        )
    )

    fun createType(
        orgId: UUID,
        key: String = "contact_card",
        version: Int = 1,
        strictness: ValidationScope = ValidationScope.SOFT,
        schema: Schema<String> = Schema(
            key = SchemaType.OBJECT
        ),
        deleted: Boolean = false,
        nesting: BlockTypeNesting = BlockTypeNesting(
            max = null,
            allowedTypes = listOf("contact_card")
        )
    ): BlockTypeEntity = BlockTypeEntity(
        id = UUID.randomUUID(),
        key = key,
        displayName = "Contact",
        description = "Contact type",
        workspaceId = orgId,
        system = false,
        version = version,
        strictness = strictness,
        schema = schema,
        displayStructure = BlockDisplay(
            form = FormStructure(emptyMap()),
            render = BlockRenderStructure(
                version = 1,
                layoutGrid = LayoutGrid(
                    layout = GridRect(
                        x = 0,
                        y = 0,
                        width = 12,
                        height = 12,
                        locked = false,
                        margin = null
                    ),
                    items = emptyList()
                ),
                components = emptyMap()
            )
        ),
        nesting = nesting
    ).also {
        it.deleted = deleted
    }

    fun createBlock(
        id: UUID,
        orgId: UUID,
        type: BlockTypeEntity,
    ): BlockEntity = BlockEntity(
        id = id,
        workspaceId = orgId,
        type = type,
        name = "Test Block",
        payload = BlockContentMetadata(data = emptyMap(), meta = BlockMeta()),
    )


    /**
     * Creates a default root schema for a block.
     *
     * @return A BlockSchema whose name is "root".
     */
    fun generateSchema(): Schema<String> = Schema(
        key = SchemaType.OBJECT,
    )

    /**
     * Creates a default BlockDisplay with an empty form structure and a text render.
     *
     * @return A BlockDisplay whose form is an empty FormStructure and whose render is a BlockRenderStructure using `ComponentType.TEXT` with no properties.
     */
    fun generateDisplay(): BlockDisplay = BlockDisplayFactory.display(
        render = BlockDisplayFactory.contactWithAccountSummary()
    )

    /**
     * Creates a simple Block model for testing.
     */
    fun createBlockModel(
        id: UUID = UUID.randomUUID(),
        orgId: UUID,
        type: BlockType,
        name: String? = "Test Block",
        payload: BlockContentMetadata = BlockContentMetadata(data = emptyMap(), meta = BlockMeta()),
    ): Block = Block(
        id = id,
        name = name,
        workspaceId = orgId,
        type = type,
        payload = payload,

        )

    /**
     * Creates a simple ContentNode for testing.
     */
    fun createNode(
        orgId: UUID,
        blockId: UUID = UUID.randomUUID(),
        type: BlockType,
        name: String? = "Test Node",
        children: List<Node>? = null
    ): ContentNode = ContentNode(
        block = createBlockModel(
            id = blockId,
            orgId = orgId,
            type = type,
            name = name
        ),
        children = children
    )

    /**
     * Creates an AddBlockOperation for testing.
     */
    fun createAddOperation(
        blockId: UUID = UUID.randomUUID(),
        orgId: UUID,
        type: BlockType,
        parentId: UUID? = null,
        index: Int? = null
    ): AddBlockOperation = AddBlockOperation(
        blockId = blockId,
        block = createNode(orgId = orgId, blockId = blockId, type = type),
        parentId = parentId,
        index = index
    )

    /**
     * Creates a RemoveBlockOperation for testing.
     */
    fun createRemoveOperation(
        blockId: UUID,
        parentId: UUID? = null,
        childrenIds: Map<UUID, UUID> = emptyMap()
    ): RemoveBlockOperation = RemoveBlockOperation(
        blockId = blockId,
        parentId = parentId,
        childrenIds = childrenIds
    )

    /**
     * Creates an UpdateBlockOperation for testing.
     */
    fun createUpdateOperation(
        blockId: UUID,
        orgId: UUID,
        type: BlockType
    ): UpdateBlockOperation = UpdateBlockOperation(
        blockId = blockId,
        updatedContent = createNode(orgId = orgId, blockId = blockId, type = type)
    )

    /**
     * Creates a MoveBlockOperation for testing.
     */
    fun createMoveOperation(
        blockId: UUID,
        fromParentId: UUID? = null,
        toParentId: UUID? = null
    ): MoveBlockOperation = MoveBlockOperation(
        blockId = blockId,
        fromParentId = fromParentId,
        toParentId = toParentId
    )

    /**
     * Creates a ReorderBlockOperation for testing.
     */
    fun createReorderOperation(
        blockId: UUID,
        parentId: UUID,
        fromIndex: Int,
        toIndex: Int
    ): ReorderBlockOperation = ReorderBlockOperation(
        blockId = blockId,
        parentId = parentId,
        fromIndex = fromIndex,
        toIndex = toIndex
    )

    /**
     * Creates a StructuralOperationRequest for testing.
     */
    fun createOperationRequest(
        operation: BlockOperation,
        timestamp: ZonedDateTime = ZonedDateTime.now(),
        id: UUID = UUID.randomUUID()
    ): StructuralOperationRequest = StructuralOperationRequest(
        id = id,
        timestamp = timestamp,
        data = operation
    )

    /**
     * Creates a BlockTreeLayoutEntity for testing.
     */
    fun createTreeLayoutEntity(
        id: UUID? = UUID.randomUUID(),
        entityId: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        version: Int = 1,
        layout: TreeLayout = TreeLayout()
    ): BlockTreeLayoutEntity = BlockTreeLayoutEntity(
        id = id,
        entityId = entityId,
        workspaceId = workspaceId,
        version = version,
        layout = layout
    )

    /**
     * Creates a BlockChildEntity for testing.
     */
    fun createBlockChildEntity(
        parentId: UUID,
        childId: UUID,
        orderIndex: Int = 0
    ): BlockChildEntity = BlockChildEntity(
        id = UUID.randomUUID(),
        parentId = parentId,
        childId = childId,
        orderIndex = orderIndex
    )

    /**
     * Creates a SaveEnvironmentRequest for testing.
     */
    fun createSaveEnvironmentRequest(
        layoutId: UUID,
        workspaceId: UUID,
        operations: List<StructuralOperationRequest>,
        version: Int = 1,
        layout: TreeLayout = TreeLayout(),
        force: Boolean = false
    ): SaveEnvironmentRequest = SaveEnvironmentRequest(
        layoutId = layoutId,
        workspaceId = workspaceId,
        layout = layout,
        version = version,
        operations = operations,
    )

    /**
     * Creates a BlockTypeEntity for testing (alias for createType).
     */
    fun createTypeEntity(
        orgId: UUID,
        key: String = "test_block",
        version: Int = 1
    ): BlockTypeEntity = createType(orgId = orgId, key = key, version = version)

    /**
     * Creates a BlockEntity for testing (alias for createBlock).
     */
    fun createBlockEntity(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        type: BlockTypeEntity
    ): BlockEntity = createBlock(id = id, orgId = workspaceId, type = type)
}
