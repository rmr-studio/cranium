package cranium.core.enums.block.layout

import io.swagger.v3.oas.annotations.media.Schema

@Schema(enumAsRef = true)
enum class RenderType {
    LIST,
    COMPONENT,
    CONTAINER
}