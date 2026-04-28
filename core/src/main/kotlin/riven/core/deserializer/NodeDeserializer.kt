package riven.core.deserializer

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import riven.core.enums.block.node.NodeType
import riven.core.models.block.tree.ContentNode
import riven.core.models.block.tree.Node
import riven.core.models.block.tree.ReferenceNode
import riven.core.util.getEnumFromField

class NodeDeserializer : ValueDeserializer<Node>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Node {
        val node = ctxt.readTree(p) as JsonNode
        val nodeType = ctxt.getEnumFromField<NodeType>(
            node,
            "type",
            Node::class.java
        )

        return when (nodeType) {
            NodeType.CONTENT -> ctxt.readTreeAsValue(node, ContentNode::class.java)
            NodeType.REFERENCE -> ctxt.readTreeAsValue(node, ReferenceNode::class.java)
            else -> throw IllegalArgumentException("Unknown Node type: $nodeType")
        }
    }
}
