package cranium.core.deserializer

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import cranium.core.enums.entity.EntityPropertyType
import cranium.core.models.entity.payload.EntityAttributePayload
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.models.entity.payload.EntityAttributeRelationPayloadReference
import cranium.core.util.getEnumFromField

class EntityAttributePayloadDeserializer : ValueDeserializer<EntityAttributePayload>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): EntityAttributePayload {
        val node = ctxt.readTree(p) as JsonNode
        val attributeType = ctxt.getEnumFromField<EntityPropertyType>(
            node,
            "type",
            EntityPropertyType::class.java
        )

        return when (attributeType) {
            EntityPropertyType.ATTRIBUTE -> ctxt.readTreeAsValue(
                node,
                EntityAttributePrimitivePayload::class.java
            )

            EntityPropertyType.RELATIONSHIP -> ctxt.readTreeAsValue(
                node,
                EntityAttributeRelationPayloadReference::class.java
            )
        }
    }
}