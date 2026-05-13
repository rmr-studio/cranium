package cranium.core.models.block.layout

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import cranium.core.enums.block.layout.RenderType
import cranium.core.enums.block.node.NodeType

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class RenderContent(
    var id: String,
    val key: String,
    val renderType: RenderType,
    val blockType: NodeType
)