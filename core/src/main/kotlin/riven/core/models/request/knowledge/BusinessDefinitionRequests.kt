package cranium.core.models.request.knowledge

import cranium.core.enums.knowledge.DefinitionCategory
import cranium.core.enums.knowledge.DefinitionSource
import cranium.core.models.knowledge.AttributeRef
import java.util.*

data class CreateBusinessDefinitionRequest(
    val term: String,
    val definition: String,
    val category: DefinitionCategory,
    val source: DefinitionSource = DefinitionSource.MANUAL,
    val entityTypeRefs: List<UUID> = emptyList(),
    val attributeRefs: List<AttributeRef> = emptyList(),
    val isCustomized: Boolean = false,
)

data class UpdateBusinessDefinitionRequest(
    val term: String,
    val definition: String,
    val category: DefinitionCategory,
    val entityTypeRefs: List<UUID> = emptyList(),
    val attributeRefs: List<AttributeRef> = emptyList(),
    val version: Int,
)
