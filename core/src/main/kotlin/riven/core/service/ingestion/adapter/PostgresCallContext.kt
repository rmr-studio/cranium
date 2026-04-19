package riven.core.service.ingestion.adapter

import java.util.UUID

/**
 * Call context for the Phase 3 Postgres adapter.
 *
 * Carries per-call pool + schema coordinates. The adapter consults
 * [connectionId] to resolve a pooled DataSource and [tableName] + cursor
 * metadata to build the fetch SQL. Introspection calls leave [tableName] and
 * cursor fields null.
 *
 * @property workspaceId Workspace attribution — forwarded to the logger and
 *   Phase 4 orchestrator event trail.
 * @property connectionId Identifier that keys the per-connection Hikari pool
 *   and maps back to [riven.core.entity.connector.DataConnectorConnectionEntity].
 * @property schema Postgres schema to introspect / fetch from. Defaults to `public`.
 * @property tableName Null during introspection; required for fetch. Quoted
 *   verbatim on the SQL path to support mixed-case identifiers.
 * @property cursorColumn Column name to use for `WHERE <col> > ?` polling.
 *   Null means "no cursor column known" — fetch falls back to PK comparison.
 * @property primaryKeyColumn PK column used for insert-only PK-fallback fetch
 *   when [cursorColumn] is null. If both are null, fetch throws
 *   [riven.core.service.ingestion.adapter.exception.AdapterCapabilityNotSupportedException].
 * @property cursorColumnIsTimestamp Gate: when `true`, [cursorColumn] is bound
 *   as a `timestamptz` parameter; when `false`, bound as its natural JDBC type.
 */
data class PostgresCallContext(
    override val workspaceId: UUID,
    val connectionId: UUID,
    val schema: String = "public",
    val tableName: String? = null,
    val cursorColumn: String? = null,
    val primaryKeyColumn: String? = null,
    val cursorColumnIsTimestamp: Boolean = false,
) : AdapterCallContext()
