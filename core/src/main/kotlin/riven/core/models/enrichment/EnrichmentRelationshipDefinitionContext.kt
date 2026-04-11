package riven.core.models.enrichment

/**
 * Semantic definition context for a single relationship type on the entity type being enriched.
 *
 * Used in Section 6 of the enriched text to explain what each relationship type represents
 * in business terms (e.g. "Support Tickets — escalation records from the help desk system").
 * When [definition] is null the relationship type has no semantic annotation yet.
 *
 * Temporal-serializable: String-only fields.
 */
data class EnrichmentRelationshipDefinitionContext(
    /** Human-readable relationship type name (e.g. "Support Tickets"). */
    val name: String,
    /** Optional semantic definition from EntityTypeSemanticMetadata, or null if not set. */
    val definition: String?,
)
