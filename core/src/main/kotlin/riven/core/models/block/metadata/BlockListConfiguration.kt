package cranium.core.models.block.metadata

data class BlockListConfiguration(
    override val listType: List<String>? = null,
    override val allowDuplicates: Boolean = false,
    override val display: ListDisplayConfig = ListDisplayConfig(),
    override val config: ListConfig = ListConfig()
) : ListMetadata<List<String>>