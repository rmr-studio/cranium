package cranium.core.deserializer


import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import cranium.core.enums.block.node.ReferenceType
import cranium.core.models.block.tree.BlockTreeReference
import cranium.core.models.block.tree.EntityReference
import cranium.core.models.block.tree.ReferencePayload
import cranium.core.util.getEnumFromField

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
