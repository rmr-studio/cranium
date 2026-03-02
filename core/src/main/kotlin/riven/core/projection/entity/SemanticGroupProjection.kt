package riven.core.projection.entity

import riven.core.enums.entity.semantics.SemanticGroup
import java.util.*

/**
 * Projection interface for (id, semanticGroup) pairs from entity type queries.
 * Spring Data JPA maps JPQL constructor expressions to these accessors.
 */
interface SemanticGroupProjection {
    fun getId(): UUID
    fun getSemanticGroup(): SemanticGroup
}
