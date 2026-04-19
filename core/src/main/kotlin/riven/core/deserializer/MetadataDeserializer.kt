package riven.core.deserializer

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import riven.core.enums.block.structure.BlockMetadataType
import riven.core.models.block.metadata.BlockContentMetadata
import riven.core.models.block.metadata.BlockReferenceMetadata
import riven.core.models.block.metadata.EntityReferenceMetadata
import riven.core.models.block.metadata.Metadata
import riven.core.util.getEnumFromField

/**
 * Jackson deserializer for [Metadata].
 * Ensures all implementations of this sealed interface are properly deserialized.
 */
class MetadataDeserializer : ValueDeserializer<Metadata>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Metadata {
        val node = ctxt.readTree(p) as JsonNode
        val metadataType = ctxt.getEnumFromField<BlockMetadataType>(
            node,
            "type",
            Metadata::class.java
        )

        return when (metadataType) {
            BlockMetadataType.CONTENT -> ctxt.readTreeAsValue(node, BlockContentMetadata::class.java)
            BlockMetadataType.ENTITY_REFERENCE -> ctxt.readTreeAsValue(node, EntityReferenceMetadata::class.java)
            BlockMetadataType.BLOCK_REFERENCE -> ctxt.readTreeAsValue(node, BlockReferenceMetadata::class.java)
        }
    }
}
