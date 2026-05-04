package riven.core.models.entity.knowledge

/**
 * Character budget abstraction consumed by [riven.core.service.enrichment.EntityKnowledgeViewProjector].
 *
 * Phase 2 ships this type so the projector stub compiles. Phase 3 PROJ-01..PROJ-06 implements the
 * sequential truncation phase methods (protectIdentity, dropFreetextIfOverBudget,
 * dropRelationalRefsIfOverBudget, trimKnowledgeByRecency) that consume this budget.
 *
 * Note: [maxChars] is intentionally character-based (Decision 8A). Token-count-based budgeting
 * (tokenCount) is explicitly deferred — [TruncationResult] carries no tokenCount field.
 *
 * @property maxChars Maximum total characters permitted in the embedding text output.
 */
data class TruncationBudget(
    val maxChars: Int,
)
