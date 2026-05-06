package riven.core.models.entity.knowledge

import java.time.ZonedDateTime
import java.util.*

/**
 * A single glossary-derived narrative attached to a [TypeNarrativeSection] or [AttributeSection].
 *
 * Sourced from the DEFINES relationship edge between a glossary entity and the target entity type
 * or attribute. Multiple glossary entries can define the same target; all are carried here so
 * Phase 3 truncation can rank by recency.
 *
 * @property sourceEntityId The UUID of the glossary entity that authored this DEFINES edge.
 * @property sourceLabel Identifier value of the glossary entity (human-readable name).
 * @property narrative The glossary entity's "definition" attribute value.
 * @property createdAt Edge createdAt timestamp — used by Phase 3 trimKnowledgeByRecency.
 */
data class GlossaryNarrative(
    val sourceEntityId: UUID,
    val sourceLabel: String,
    val narrative: String,
    val createdAt: ZonedDateTime,
)
