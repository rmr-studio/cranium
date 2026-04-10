package riven.core.models.request.knowledge

import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import java.util.*

data class CreateBusinessDefinitionRequest(
    val term: String,
    val definition: String,
    val category: DefinitionCategory,
    val source: DefinitionSource = DefinitionSource.MANUAL,
    val entityTypeRefs: List<UUID> = emptyList(),
    val attributeRefs: List<UUID> = emptyList(),
    val isCustomized: Boolean = false,
)

data class UpdateBusinessDefinitionRequest(
    val term: String,
    val definition: String,
    val category: DefinitionCategory,
    val entityTypeRefs: List<UUID> = emptyList(),
    val attributeRefs: List<UUID> = emptyList(),
    val version: Int,
)
