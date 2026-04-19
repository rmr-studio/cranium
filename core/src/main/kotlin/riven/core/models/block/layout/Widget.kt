package riven.core.models.block.layout

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.annotation.JsonDeserialize
import riven.core.deserializer.WidgetDeserializer

/**
 * Represents a single node/widget in the Gridstack layout
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = WidgetDeserializer::class)
data class Widget(
    // Unique identifier for this widget (typically block ID)
    var id: String,

    // Position and dimensions
    val x: Int,
    val y: Int,
    val w: Int,  // width in grid columns

    val h: Int? = null,  // height in grid rows

    // Optional constraints
    val minW: Int? = null,
    val minH: Int? = null,
    val maxW: Int? = null,
    val maxH: Int? = null,

    // Widget behavior
    val autoPosition: Boolean? = null,
    val locked: Boolean? = null,
    val noResize: Boolean? = null,
    val noMove: Boolean? = null,

    // Content - typically serialized block metadata
    val content: RenderContent? = null,

    // Nested grid configuration (for container blocks)
    val subGridOpts: TreeLayout? = null
)

