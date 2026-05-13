package cranium.core.models.block.metadata

import cranium.core.enums.block.node.ListFilterLogicType
import cranium.core.enums.block.structure.BlockListOrderingMode

interface ListMetadata<T> {
    val config: ListConfig
    val display: ListDisplayConfig
    val allowDuplicates: Boolean
    val listType: T?
}

data class ListConfig(
    val mode: BlockListOrderingMode = BlockListOrderingMode.MANUAL,
    // Used when mode=SORTED
    val sort: SortSpec? = null,
    val filters: List<FilterSpec> = listOf(),
    val filterLogic: ListFilterLogicType = ListFilterLogicType.AND,
)


data class ListDisplayConfig(
    val itemSpacing: Int = 8,
    val showDragHandles: Boolean = true, // Auto-false when mode=SORTED
    val emptyMessage: String = "No items",
    val paging: PagingSpec? = null,
)