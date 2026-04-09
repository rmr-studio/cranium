package riven.core.service.util.factory.knowledge

import riven.core.entity.knowledge.WorkspaceBusinessDefinitionEntity
import riven.core.enums.knowledge.DefinitionCategory
import riven.core.enums.knowledge.DefinitionSource
import riven.core.enums.knowledge.DefinitionStatus
import java.util.*

object BusinessDefinitionFactory {

    fun createDefinition(
        id: UUID = UUID.randomUUID(),
        workspaceId: UUID,
        term: String = "Retention Rate",
        normalizedTerm: String = "retention rate",
        definition: String = "A customer is retained if they have an active subscription 90 days after first purchase",
        category: DefinitionCategory = DefinitionCategory.METRIC,
        status: DefinitionStatus = DefinitionStatus.ACTIVE,
        source: DefinitionSource = DefinitionSource.MANUAL,
        compiledParams: Map<String, Any>? = null,
        entityTypeRefs: List<UUID> = emptyList(),
        attributeRefs: List<UUID> = emptyList(),
        isCustomized: Boolean = false,
        version: Int = 0,
        deleted: Boolean = false,
    ): WorkspaceBusinessDefinitionEntity = WorkspaceBusinessDefinitionEntity(
        id = id,
        workspaceId = workspaceId,
        term = term,
        normalizedTerm = normalizedTerm,
        definition = definition,
        category = category,
        status = status,
        source = source,
        compiledParams = compiledParams,
        entityTypeRefs = entityTypeRefs,
        attributeRefs = attributeRefs,
        isCustomized = isCustomized,
        version = version,
    ).also {
        it.deleted = deleted
    }
}
