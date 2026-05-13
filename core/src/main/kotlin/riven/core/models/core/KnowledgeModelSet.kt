package cranium.core.models.core

import cranium.core.models.core.base.GlossaryTermModel
import cranium.core.models.core.base.NoteModel

/**
 * Knowledge-surface entity types. surface_role=KNOWLEDGE on every model.
 * Cross-model relationships in this set live on system-managed
 * relationship_definitions rows seeded per workspace via SystemRelationshipType,
 * not in additionalRelationships — KNOWLEDGE edges target arbitrary entity
 * types and don't fit the source/target-model-key shape.
 */
val KNOWLEDGE_MODELS = CoreModelSet(
    manifestKey = "knowledge",
    name = "Knowledge",
    description = "Knowledge-surface entity types: notes, glossary terms, decisions, policies, SOPs, and other internal-knowledge atoms.",
    models = listOf(
        NoteModel,
        GlossaryTermModel,
    ),
    additionalRelationships = emptyList(),
)
