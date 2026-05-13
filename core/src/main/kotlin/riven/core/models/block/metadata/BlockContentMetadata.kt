package cranium.core.models.block.metadata

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.enums.block.structure.BlockMetadataType
import cranium.core.models.common.json.JsonObject

@JsonTypeName("content")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class BlockContentMetadata(
    @get:Schema(type = "object", implementation = Any::class)
    var data: JsonObject = emptyMap(),
    override val meta: BlockMeta = BlockMeta(),
    override val deletable: Boolean = true,
    override val readonly: Boolean = false,
    val listConfig: BlockListConfiguration? = null
) : Metadata {
    override val type: BlockMetadataType = BlockMetadataType.CONTENT
}

