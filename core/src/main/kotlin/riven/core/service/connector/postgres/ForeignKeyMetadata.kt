package riven.core.service.connector.postgres

/**
 * Surface format for a foreign-key relationship discovered during Postgres
 * schema introspection. Supplied alongside [riven.core.models.ingestion.adapter.SchemaIntrospectionResult]
 * so plan 03-03 can build a `RelationshipDefinitionEntity` from simple FKs
 * without re-querying `pg_constraint`.
 *
 * Composite (multi-column) FKs are surfaced with [isComposite] = true but not
 * consumed by the relationship builder — they are logged and skipped, see
 * 03-CONTEXT.md.
 *
 * @property sourceTable Table declaring the FK (the child table).
 * @property sourceColumn Column on [sourceTable]; only the first column is
 *   populated for composite keys.
 * @property targetTable Referenced table (the parent table).
 * @property targetColumn Column on [targetTable]; only the first column is
 *   populated for composite keys.
 * @property isComposite True when the underlying pg FK spans multiple columns.
 */
data class ForeignKeyMetadata(
    val sourceTable: String,
    val sourceColumn: String,
    val targetTable: String,
    val targetColumn: String,
    val isComposite: Boolean,
)
