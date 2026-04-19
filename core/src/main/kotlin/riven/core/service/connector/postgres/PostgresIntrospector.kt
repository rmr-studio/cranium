package riven.core.service.connector.postgres

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import riven.core.models.ingestion.adapter.ColumnSchema
import riven.core.models.ingestion.adapter.SchemaIntrospectionResult
import riven.core.models.ingestion.adapter.TableSchema
import javax.sql.DataSource

/**
 * Introspects a Postgres schema via `INFORMATION_SCHEMA` + `pg_constraint` +
 * `pg_enum`. Produces an [IntrospectionResult] combining the neutral
 * [SchemaIntrospectionResult] with a per-FK metadata list.
 *
 * Covers PG-07. Composite FKs are surfaced with `isComposite = true` and
 * logged; plan 03-03 skips them when materialising relationships.
 */
@Component
@ConditionalOnProperty(prefix = "riven.connector", name = ["enabled"], havingValue = "true")
class PostgresIntrospector(
    private val logger: KLogger,
) {

    internal fun introspect(dataSource: DataSource, schema: String): IntrospectionResult {
        dataSource.connection.use { conn ->
            val tables = loadTables(conn, schema)
            val columnsByTable = loadColumns(conn, schema)
            val fkList = loadForeignKeys(conn, schema)

            val tableSchemas = tables.map { tableName ->
                val cols = columnsByTable[tableName].orEmpty()
                TableSchema(name = tableName, columns = cols)
            }
            return IntrospectionResult(
                schema = SchemaIntrospectionResult(tables = tableSchemas),
                foreignKeys = fkList,
            )
        }
    }

    // ------ Private helpers ------

    private fun loadTables(conn: java.sql.Connection, schema: String): List<String> {
        val sql = """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = ?
              AND table_type = 'BASE TABLE'
            ORDER BY table_name
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schema)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<String>()
                while (rs.next()) result.add(rs.getString(1))
                return result
            }
        }
    }

    private fun loadColumns(
        conn: java.sql.Connection,
        schema: String,
    ): Map<String, List<ColumnSchema>> {
        val sql = """
            SELECT table_name, column_name, udt_name, is_nullable, ordinal_position
            FROM information_schema.columns
            WHERE table_schema = ?
            ORDER BY table_name, ordinal_position
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schema)
            stmt.executeQuery().use { rs ->
                val grouped = linkedMapOf<String, MutableList<ColumnSchema>>()
                while (rs.next()) {
                    val table = rs.getString(1)
                    val column = ColumnSchema(
                        name = rs.getString(2),
                        typeLiteral = rs.getString(3),
                        nullable = rs.getString(4).equals("YES", ignoreCase = true),
                    )
                    grouped.getOrPut(table) { mutableListOf() }.add(column)
                }
                return grouped
            }
        }
    }

    private fun loadForeignKeys(
        conn: java.sql.Connection,
        schema: String,
    ): List<ForeignKeyMetadata> {
        // Query pg_constraint for FKs; conkey/confkey are smallint[] arrays.
        val sql = """
            SELECT
                src_rel.relname  AS source_table,
                src_ns.nspname   AS source_schema,
                con.conkey       AS source_cols,
                tgt_rel.relname  AS target_table,
                con.confkey      AS target_cols,
                con.conrelid     AS src_oid,
                con.confrelid    AS tgt_oid
            FROM pg_constraint con
            JOIN pg_class    src_rel ON src_rel.oid = con.conrelid
            JOIN pg_namespace src_ns ON src_ns.oid = src_rel.relnamespace
            JOIN pg_class    tgt_rel ON tgt_rel.oid = con.confrelid
            WHERE con.contype = 'f'
              AND src_ns.nspname = ?
            ORDER BY src_rel.relname, con.conname
        """.trimIndent()
        val fks = mutableListOf<ForeignKeyMetadata>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, schema)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val sourceTable = rs.getString("source_table")
                    val targetTable = rs.getString("target_table")
                    val sourceCols = rs.getArray("source_cols")?.array as? Array<*> ?: emptyArray<Any>()
                    val targetCols = rs.getArray("target_cols")?.array as? Array<*> ?: emptyArray<Any>()
                    val srcOid = rs.getLong("src_oid")
                    val tgtOid = rs.getLong("tgt_oid")
                    val isComposite = sourceCols.size > 1

                    if (sourceCols.isEmpty() || targetCols.isEmpty()) continue

                    val sourceColumnName = resolveColumnName(conn, srcOid, (sourceCols[0] as Number).toInt())
                    val targetColumnName = resolveColumnName(conn, tgtOid, (targetCols[0] as Number).toInt())

                    if (isComposite) {
                        logger.info {
                            "Retaining composite FK on $sourceTable($sourceColumnName,...) -> " +
                                "$targetTable($targetColumnName,...) — metadata kept, " +
                                "relationship materialisation (plan 03-03) unsupported for composite keys."
                        }
                    }

                    fks.add(
                        ForeignKeyMetadata(
                            sourceTable = sourceTable,
                            sourceColumn = sourceColumnName,
                            targetTable = targetTable,
                            targetColumn = targetColumnName,
                            isComposite = isComposite,
                        ),
                    )
                }
            }
        }
        return fks
    }

    /**
     * Resolve an `(attrelid, attnum)` pair back to a column name via
     * `pg_attribute`. Called once per FK; cheap.
     */
    private fun resolveColumnName(conn: java.sql.Connection, relOid: Long, attNum: Int): String {
        conn.prepareStatement(
            "SELECT attname FROM pg_attribute WHERE attrelid = ? AND attnum = ?",
        ).use { stmt ->
            stmt.setLong(1, relOid)
            stmt.setInt(2, attNum)
            stmt.executeQuery().use { rs ->
                if (rs.next()) return rs.getString(1)
                throw IllegalStateException(
                    "Failed to resolve column name from pg_attribute: relOid=$relOid attNum=$attNum. " +
                        "FK metadata would carry a placeholder — refusing to propagate.",
                )
            }
        }
    }
}
