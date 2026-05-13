package cranium.core.models.block.layout

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import cranium.core.models.block.layout.options.ColumnOptions
import cranium.core.models.block.layout.options.DraggableOptions
import cranium.core.models.block.layout.options.ResizableOptions
import cranium.core.models.common.json.JsonValue

/**
 * Represents the complete tree layout structure from Gridstack.save()
 * This captures all grid configuration and positioning metadata that can be
 * persisted and later restored via Gridstack.load()
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class TreeLayout(
    // Grid configuration options
    val acceptWidgets: Boolean? = null,
    val alwaysShowResizeHandle: Boolean? = null,
    val animate: Boolean? = null,
    val auto: Boolean? = null,
    val cellHeight: Int? = null,
    val resizable: ResizableOptions? = null,
    val draggable: DraggableOptions? = null,
    val margin: Int? = null,
    val marginTop: Int? = null,
    val marginRight: Int? = null,
    val marginBottom: Int? = null,
    val marginLeft: Int? = null,
    @field:Schema(
        description = "Number of columns or the string 'auto'",
        oneOf = [Int::class, String::class]
    )
    val column: JsonValue? = null,
    val columnOpts: ColumnOptions? = null,
    val disableDrag: Boolean? = null,
    val disableOneColumnMode: Boolean? = null,
    val disableResize: Boolean? = null,
    val float: Boolean? = null,
    val handle: String? = null,
    val handleClass: String? = null,
    val itemClass: String? = null,
    val maxRow: Int? = null,
    val minRow: Int? = null,
    val oneColumnSize: Int? = null,
    val placeholderClass: String? = null,
    val placeholderText: String? = null,
    val removable: Boolean? = null,
    val removeTimeout: Int? = null,
    val row: Int? = null,
    val rtl: Boolean? = null,
    val staticGrid: Boolean? = null,
    val styleInHead: Boolean? = null,
    val sizeToContent: Boolean? = null,

    // Custom layout type (e.g., "list" for vertical layouts)
    val layout: String? = null,

    // Custom CSS classes
    val `class`: String? = null,

    // Children widgets/blocks in this grid
    val children: List<Widget>? = null
)