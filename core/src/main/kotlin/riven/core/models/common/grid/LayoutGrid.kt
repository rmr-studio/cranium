package cranium.core.models.common.grid

data class LayoutGrid(
    // Unique identifier for this layout grid in terms of rendered component
    val layout: GridRect,
    val items: List<LayoutGridItem> = emptyList(),
)

data class LayoutGridItem(
    val id: String,
    val rect: GridRect,
)