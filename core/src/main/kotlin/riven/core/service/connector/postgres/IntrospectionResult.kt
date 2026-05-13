package cranium.core.service.connector.postgres

import cranium.core.models.ingestion.adapter.SchemaIntrospectionResult

/**
 * Internal sibling of [SchemaIntrospectionResult] that also carries PG foreign-key
 * metadata. Exposed via [PostgresAdapter.introspectWithFkMetadata] so plan 03-03
 * can materialise relationships without extending the Phase 1 adapter contract.
 */
internal data class IntrospectionResult(
    val schema: SchemaIntrospectionResult,
    val foreignKeys: List<ForeignKeyMetadata>,
)
