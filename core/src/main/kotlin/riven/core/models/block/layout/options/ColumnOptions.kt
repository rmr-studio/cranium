package cranium.core.models.block.layout.options

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Column-specific options for responsive layouts
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ColumnOptions(
    val breakpoints: List<BreakpointConfig>? = null,
    val layout: String? = null,
    val breakpointForWindow: String? = null,
    val columnMax: Int? = null,
    val columnWidth: Int? = null
)
