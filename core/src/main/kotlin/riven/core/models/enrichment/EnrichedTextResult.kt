package riven.core.models.enrichment

/**
 * Result of the enriched text construction step in the embedding pipeline.
 *
 * Carries the text itself along with metadata about truncation and estimated
 * token count so that downstream activities (storeEmbedding) can persist
 * the truncated flag accurately rather than hardcoding false.
 *
 * Temporal-serializable: uses only primitive types for Jackson compatibility.
 *
 * Phase 3 Plan 03 introduces real truncation logic — until then, [truncated] is
 * always false and [estimatedTokens] uses a simple char/4 approximation.
 */
data class EnrichedTextResult(
    /** The constructed enriched text ready for embedding generation. */
    val text: String,
    /** Whether the text was truncated to fit within the embedding model token limit. */
    val truncated: Boolean,
    /** Approximate token count estimated as text.length / 4. */
    val estimatedTokens: Int,
)
