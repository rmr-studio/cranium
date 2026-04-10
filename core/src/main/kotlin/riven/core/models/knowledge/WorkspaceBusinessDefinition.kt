package riven.core.models.knowledge

import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.knowledge.DefinitionStatus
import java.time.ZonedDateTime
import java.util.*

data class WorkspaceBusinessDefinition(
    val id: UUID,
    val workspaceId: UUID,
    val term: String,
    val normalizedTerm: String,
    val definition: String,
    val category: DefinitionCategory,
    val compiledParams: Map<String, Any>?,
    val status: DefinitionStatus,
    val source: DefinitionSource,
    val entityTypeRefs: List<UUID>,
    val attributeRefs: List<UUID>,
    val isCustomized: Boolean,
    val version: Int,
    val createdBy: UUID?,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
)
