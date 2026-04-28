package riven.core.deserializer

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import riven.core.enums.entity.EntityTypeRequestDefinition
import riven.core.models.request.entity.type.*
import riven.core.util.getEnumFromField


class TypeDefinitionRequestDeserializer : ValueDeserializer<TypeDefinition>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TypeDefinition {
        val node = ctxt.readTree(p) as JsonNode
        val definitionType = ctxt.getEnumFromField<EntityTypeRequestDefinition>(
            node,
            "type",
            TypeDefinition::class.java
        )

        return when (definitionType) {
            EntityTypeRequestDefinition.SAVE_RELATIONSHIP -> ctxt.readTreeAsValue(
                node,
                SaveRelationshipDefinitionRequest::class.java
            )

            EntityTypeRequestDefinition.SAVE_SCHEMA -> ctxt.readTreeAsValue(
                node,
                SaveAttributeDefinitionRequest::class.java
            )

            EntityTypeRequestDefinition.DELETE_RELATIONSHIP -> ctxt.readTreeAsValue(
                node,
                DeleteRelationshipDefinitionRequest::class.java
            )

            EntityTypeRequestDefinition.DELETE_SCHEMA -> ctxt.readTreeAsValue(
                node,
                DeleteAttributeDefinitionRequest::class.java
            )
        }
    }
}
