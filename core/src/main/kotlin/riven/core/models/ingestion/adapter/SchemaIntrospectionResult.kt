package riven.core.models.ingestion.adapter

/**
 * Result of an adapter schema introspection call.
 *
 * Shape is intentionally minimal — the Phase 1 contract carries only the fields
 * needed to materialise an `EntityType` and its attributes. Phase 3 will extend
 * with primary-key / foreign-key metadata (see roadmap requirement PG-07).
 *
 * @property tables The discovered tables (or record collections) exposed by the
 *   source.
 */
data class SchemaIntrospectionResult(
    val tables: List<TableSchema>,
)

/**
 * A single table (or record collection) discovered during schema introspection.
 *
 * @property name Source-system table / collection name. Used as the `key` seed
 *   when materialising an `EntityType`.
 * @property columns The columns exposed by the table, in source-declaration order.
 */
data class TableSchema(
    val name: String,
    val columns: List<ColumnSchema>,
)

/**
 * A single column within a discovered table.
 *
 * `typeLiteral` is the source-system type string (e.g. `"bigint"`, `"text"`,
 * `"timestamp with time zone"` for Postgres). The mapper layer (Phase 3)
 * translates this into a Riven `SchemaType`.
 *
 * @property name Source-system column name.
 * @property typeLiteral Source-system-native type string, verbatim.
 * @property nullable Whether the column permits null values in the source schema.
 */
data class ColumnSchema(
    val name: String,
    val typeLiteral: String,
    val nullable: Boolean,
)
