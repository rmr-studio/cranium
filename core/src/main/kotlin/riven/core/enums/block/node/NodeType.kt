package cranium.core.enums.block.node

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class NodeType {
    REFERENCE,
    CONTENT,

    @Schema(
        description = """
           This type is used to flag nodes that could not be properly constructed due to issues 
           such as missing data or incompatible formats.
           This enum is only used locally in the frontend client and is never serialized or sent to the backend.
        """
    )
    @Suppress("unused")
    ERROR
}
