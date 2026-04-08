package riven.core.models.enrichment

import riven.core.enums.integration.SourceType

/**
 * Summary of a single entity type within the cluster that the entity being enriched belongs to.
 *
 * A cluster groups entity types by shared source type origin (e.g. a CRM cluster might
 * contain Contact, Company, and Deal entity types all sourced from the same integration).
 *
 * Used in Section 5 of the enriched text to provide schema-level cluster context.
 * Temporal-serializable: only enum and String fields.
 */
data class EnrichmentClusterMemberContext(
    /** The source type that all entities in this cluster share. */
    val sourceType: SourceType,
    /** Human-readable entity type name within the cluster (e.g. "Company"). */
    val entityTypeName: String,
)
