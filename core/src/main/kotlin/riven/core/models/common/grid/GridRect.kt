package cranium.core.models.common.grid

data class GridRect(
    val x: Int,
    val y: Int,
    val margin: Int?,
    val width: Int,
    val height: Int,
    val locked: Boolean = false
)