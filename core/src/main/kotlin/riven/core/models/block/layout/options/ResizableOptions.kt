package cranium.core.models.block.layout.options

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Resizable configuration options
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ResizableOptions(
    // Which handles to show: e.g., "se, sw" for southeast and southwest corners
    val handles: String? = null,
    val autoHide: Boolean? = null
)
