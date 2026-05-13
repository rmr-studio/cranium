package cranium.core.models.block.display

data class BlockTypeNesting(
    val max: Int?,
    // List of allowed block type keys
    val allowedTypes: List<String>,
)