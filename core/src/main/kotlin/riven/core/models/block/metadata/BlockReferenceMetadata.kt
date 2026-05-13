package cranium.core.models.block.metadata

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import cranium.core.enums.block.structure.BlockMetadataType
import cranium.core.enums.block.structure.BlockReferenceFetchPolicy

/**
 * Metadata when a block is referencing an external block.
 */
@JsonTypeName("block_reference")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class BlockReferenceMetadata(
    override val fetchPolicy: BlockReferenceFetchPolicy = BlockReferenceFetchPolicy.LAZY,
    override val deletable: Boolean = true,
    override val readonly: Boolean = false,
    override val meta: BlockMeta = BlockMeta(),
    override val path: String = "\$.block",
    val expandDepth: Int = 1,
    val item: ReferenceItem
) : ReferenceMetadata {
    override val type: BlockMetadataType = BlockMetadataType.BLOCK_REFERENCE
}