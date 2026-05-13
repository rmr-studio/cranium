package cranium.core.models.knowledge

import cranium.core.enums.knowledge.DefinitionCategory
import cranium.core.enums.knowledge.DefinitionSource
import cranium.core.enums.knowledge.DefinitionStatus
import java.time.ZonedDateTime
import java.util.*

data class GlossaryTerm(
    val id: UUID,
    val workspaceId: UUID,
    val term: String,
    val normalizedTerm: String,
    val definition: String,
    val category: DefinitionCategory,
    val source: DefinitionSource,
    val entityTypeRefs: List<UUID>,
    val attributeRefs: List<AttributeRef>,
    val isCustomized: Boolean,
    val createdBy: UUID?,
    val createdAt: ZonedDateTime?,
    val updatedAt: ZonedDateTime?,
)
