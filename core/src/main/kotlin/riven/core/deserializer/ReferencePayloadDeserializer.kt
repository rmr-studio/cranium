package riven.core.deserializer


import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import riven.core.enums.block.node.ReferenceType
import riven.core.models.block.tree.BlockTreeReference
import riven.core.models.block.tree.EntityReference
import riven.core.models.block.tree.ReferencePayload
import riven.core.util.getEnumFromField

/**
 * Jackson deserializer for [ReferencePayload].
 * Ensures all implementations of this sealed interface are properly deserialized.
 */
class ReferencePayloadDeserializer : ValueDeserializer<ReferencePayload>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): ReferencePayload {
        val payload = ctxt.readTree(p) as JsonNode
        val referenceType = ctxt.getEnumFromField<ReferenceType>(
            payload,
            "type",
            ReferenceType::class.java
        )

        return when (referenceType) {
            ReferenceType.BLOCK -> ctxt.readTreeAsValue(payload, BlockTreeReference::class.java)
            ReferenceType.ENTITY -> ctxt.readTreeAsValue(payload, EntityReference::class.java)
        }
    }
}
