package cranium.core.models.block.metadata

import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.deserializer.MetadataDeserializer
import cranium.core.enums.block.structure.BlockMetadataType

@JsonDeserialize(using = MetadataDeserializer::class)
@Schema(hidden = true)
sealed interface Metadata {
    val type: BlockMetadataType

    /**
     * Indicates if the block is read-only.
     * This will affect:
     *  - Whether the block can be edited in the UI.
     *  - Whether or not a list block can have items added/removed.
     */
    val readonly: Boolean
    val meta: BlockMeta
    val deletable: Boolean
}
