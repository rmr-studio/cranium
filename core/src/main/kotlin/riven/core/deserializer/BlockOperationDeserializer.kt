package cranium.core.deserializer

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import cranium.core.enums.block.request.BlockOperationType
import cranium.core.models.block.operation.*
import cranium.core.util.getEnumFromField

/**
 * Jackson deserializer for [BlockOperation].
 * Ensures all implementations of this sealed interface are properly deserialized.
 */
class BlockOperationDeserializer : ValueDeserializer<BlockOperation>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): BlockOperation {
        val operation = ctxt.readTree(p) as JsonNode
        val operationType = ctxt.getEnumFromField<BlockOperationType>(
            operation,
            "type",
            BlockOperation::class.java
        )

        return when (operationType) {
            BlockOperationType.ADD_BLOCK -> ctxt.readTreeAsValue(operation, AddBlockOperation::class.java)
            BlockOperationType.REMOVE_BLOCK -> ctxt.readTreeAsValue(operation, RemoveBlockOperation::class.java)
            BlockOperationType.UPDATE_BLOCK -> ctxt.readTreeAsValue(operation, UpdateBlockOperation::class.java)
            BlockOperationType.REORDER_BLOCK -> ctxt.readTreeAsValue(operation, ReorderBlockOperation::class.java)
            BlockOperationType.MOVE_BLOCK -> ctxt.readTreeAsValue(operation, MoveBlockOperation::class.java)
        }
    }
}
