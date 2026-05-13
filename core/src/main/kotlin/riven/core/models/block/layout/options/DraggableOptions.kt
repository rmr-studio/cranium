package cranium.core.models.block.layout.options

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Draggable configuration options
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class DraggableOptions(
    // CSS selector for elements that should not trigger dragging
    val cancel: String? = null,
    // Delay before drag starts (in milliseconds)
    val pause: Int? = null,
    val handle: String? = null,
    val appendTo: String? = null,
    val scroll: Boolean? = null
)

