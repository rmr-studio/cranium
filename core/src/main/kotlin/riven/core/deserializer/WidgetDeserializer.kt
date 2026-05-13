package cranium.core.deserializer

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import cranium.core.models.block.layout.RenderContent
import cranium.core.models.block.layout.TreeLayout
import cranium.core.models.block.layout.Widget

/**
 * Custom deserializer for Widget that handles the stringified content field from Gridstack.
 * Gridstack stores the content as a JSON string, so we need to parse it twice.
 */
class WidgetDeserializer() : ValueDeserializer<Widget>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Widget {
        val node: JsonNode = ctxt.readTree(p) as JsonNode

        // Parse standard fields
        val id = node.get("id").asString()
        val x = node.get("x").asInt()
        val y = node.get("y").asInt()
        val w = node.get("w").asInt()

        val h = node.get("h")?.asInt()

        // Parse optional constraint fields
        val minW = node.get("minW")?.asInt()
        val minH = node.get("minH")?.asInt()
        val maxW = node.get("maxW")?.asInt()
        val maxH = node.get("maxH")?.asInt()

        // Parse optional behavior fields
        val autoPosition = node.get("autoPosition")?.asBoolean()
        val locked = node.get("locked")?.asBoolean()
        val noResize = node.get("noResize")?.asBoolean()
        val noMove = node.get("noMove")?.asBoolean()

        // Parse content - handle both string and object formats
        val content: RenderContent? = node.get("content")?.let { contentNode ->
            if (contentNode.isString) {
                // Content is a JSON string, parse it
                val contentJson = contentNode.asString()
                if (contentJson.isNotBlank()) {
                    // Re-parse the embedded JSON string through the same mapper.
                    val embeddedTree = ctxt.tokenStreamFactory()
                        .createParser(ctxt, contentJson)
                        .use { embeddedParser -> ctxt.readTree(embeddedParser) }
                    ctxt.readTreeAsValue(embeddedTree, RenderContent::class.java)
                } else {
                    null
                }
            } else {
                // Content is already an object, deserialize directly
                ctxt.readTreeAsValue(contentNode, RenderContent::class.java)
            }
        }

        // Parse nested subGridOpts
        val subGridOpts: TreeLayout? = node.get("subGridOpts")?.let { subGridNode ->
            ctxt.readTreeAsValue(subGridNode, TreeLayout::class.java)
        }

        return Widget(
            id = id,
            x = x,
            y = y,
            w = w,
            h = h,
            minW = minW,
            minH = minH,
            maxW = maxW,
            maxH = maxH,
            autoPosition = autoPosition,
            locked = locked,
            noResize = noResize,
            noMove = noMove,
            content = content,
            subGridOpts = subGridOpts
        )
    }
}
