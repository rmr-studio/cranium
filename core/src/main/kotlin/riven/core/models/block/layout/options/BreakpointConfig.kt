package cranium.core.models.block.layout.options

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Responsive breakpoint configuration
 * @property width Screen width threshold in pixels (must be positive)
 * @property columns Number of columns at this breakpoint (must be positive)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class BreakpointConfig(
    @field:JsonProperty("w")
    val width: Int,
    @field:JsonProperty("c")
    val columns: Int
)