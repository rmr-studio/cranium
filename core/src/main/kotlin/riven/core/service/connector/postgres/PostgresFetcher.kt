package riven.core.service.connector.postgres

import tools.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import riven.core.configuration.properties.ConnectorPoolProperties
import riven.core.enums.common.validation.SchemaType
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.models.ingestion.adapter.RecordBatch
import riven.core.models.ingestion.adapter.SourceRecord
import riven.core.service.ingestion.adapter.PostgresCallContext
import riven.core.service.ingestion.adapter.exception.AdapterCapabilityNotSupportedException
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import javax.sql.DataSource

/**
 * Builds and runs the cursor-or-PK-fallback SQL for the Postgres adapter. Maps
 * each row into a [SourceRecord] whose payload is
 * `Map<columnName, EntityAttributePrimitivePayload>` — typed values that bypass
 * `SchemaMappingService` per PG-05.
 *
 * Uses a server-side cursor (autoCommit = false + fetchSize) so arbitrarily
 * large result sets don't OOM the worker.
 */
@Component
@ConditionalOnProperty(prefix = "riven.connector", name = ["enabled"], havingValue = "true")
class PostgresFetcher(
    private val props: ConnectorPoolProperties,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger,
) {

    fun fetch(
        dataSource: DataSource,
        ctx: PostgresCallContext,
        cursor: String?,
        limit: Int,
    ): RecordBatch {
        val tableName = requireNotNull(ctx.tableName) {
            "PostgresCallContext.tableName must be set for fetch"
        }
        val effectiveLimit = minOf(limit, props.defaultBatchSize).coerceAtLeast(1)
        logger.debug {
            "PostgresFetcher.fetch schema=${ctx.schema} table=$tableName effectiveLimit=$effectiveLimit"
        }

        val (sql, cursorBinder, cursorExtractorColumn) = buildQuery(ctx, tableName, cursor)

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.fetchSize = props.defaultBatchSize
                    cursorBinder(stmt, cursor)
                    // The LIMIT placeholder is always the LAST parameter.
                    // Fetch one extra row so we can derive `hasMore` without a
                    // separate COUNT query: size > effectiveLimit ⇒ more rows exist.
                    stmt.setInt(stmt.parameterMetaData.parameterCount, effectiveLimit + 1)

                    stmt.executeQuery().use { rs ->
                        val meta = rs.metaData
                        val columnCount = meta.columnCount
                        val columnNames = (1..columnCount).map { meta.getColumnName(it) }
                        val columnTypeLiterals = (1..columnCount).map { meta.getColumnTypeName(it) }
                        val pkColumn = ctx.primaryKeyColumn

                        val buffer = mutableListOf<SourceRecord>()
                        val cursorBuffer = mutableListOf<String?>()
                        while (rs.next()) {
                            val payload = linkedMapOf<String, EntityAttributePrimitivePayload>()
                            for (i in 1..columnCount) {
                                val colName = columnNames[i - 1]
                                val pgType = columnTypeLiterals[i - 1]
                                val isPrimaryKey = (pkColumn != null && colName.equals(pkColumn, ignoreCase = true))
                                val rawValue = rs.getObject(i)
                                val converted = convertValue(rawValue, pgType)
                                val schemaType = PgTypeMapper.toSchemaType(
                                    pgType = pgType,
                                    isPrimaryKey = isPrimaryKey,
                                )
                                payload[colName] = EntityAttributePrimitivePayload(
                                    value = converted,
                                    schemaType = schemaType,
                                )
                            }

                            // externalId: PK value if available, else cursor column value, else row-index fallback
                            val externalIdColumn = pkColumn ?: ctx.cursorColumn
                            val externalIdRaw = if (externalIdColumn != null) {
                                rs.getObject(externalIdColumn)
                            } else null
                            val externalId = externalIdRaw?.toString() ?: buffer.size.toString()

                            @Suppress("UNCHECKED_CAST")
                            val payloadAsAny = payload.toMap() as Map<String, Any?>
                            buffer.add(
                                SourceRecord(
                                    externalId = externalId,
                                    payload = payloadAsAny,
                                    sourceMetadata = mapOf(
                                        "table" to tableName,
                                        "schema" to ctx.schema,
                                    ),
                                ),
                            )

                            cursorBuffer.add(
                                cursorExtractorColumn?.let { rs.getObject(it)?.toString() },
                            )
                        }

                        val hasMore = buffer.size > effectiveLimit
                        val records = if (hasMore) buffer.take(effectiveLimit) else buffer.toList()
                        val lastRowCursorValue = cursorBuffer
                            .take(records.size)
                            .lastOrNull { it != null }
                        return RecordBatch(
                            records = records,
                            nextCursor = lastRowCursorValue,
                            hasMore = hasMore,
                        )
                    }
                }
            } finally {
                // Best-effort rollback — we only SELECTed, so no state to commit.
                try { conn.rollback() } catch (_: Exception) { /* ignore */ }
            }
        }
    }

    // ------ Private helpers ------

    /**
     * Builds the fetch SQL for [ctx]. Returns the SQL text, a binder that
     * installs the cursor parameter (or no-op for first-time fetches), and the
     * column name to read on each row for computing the `nextCursor` of the
     * batch.
     */
    private fun buildQuery(
        ctx: PostgresCallContext,
        tableName: String,
        cursor: String?,
    ): Triple<String, (PreparedStatement, String?) -> Unit, String?> {
        val schemaQ = PgIdent.quote(ctx.schema)
        val tableQ = PgIdent.quote(tableName)
        val cursorColumn = ctx.cursorColumn
        val pkColumn = ctx.primaryKeyColumn

        return when {
            cursorColumn != null && ctx.cursorColumnIsTimestamp -> {
                val cursorQ = PgIdent.quote(cursorColumn)
                val sql = """
                    SELECT * FROM $schemaQ.$tableQ
                    WHERE $cursorQ > ?::timestamptz
                    ORDER BY $cursorQ ASC
                    LIMIT ?
                """.trimIndent()
                val binder: (PreparedStatement, String?) -> Unit = { stmt, c ->
                    val instant = c?.let { Instant.parse(it) } ?: Instant.EPOCH
                    stmt.setObject(1, Timestamp.from(instant))
                }
                Triple(sql, binder, cursorColumn)
            }
            cursorColumn != null -> {
                val cursorQ = PgIdent.quote(cursorColumn)
                val sql = if (cursor != null) {
                    """
                    SELECT * FROM $schemaQ.$tableQ
                    WHERE $cursorQ::text > ?
                    ORDER BY $cursorQ ASC
                    LIMIT ?
                    """.trimIndent()
                } else {
                    """
                    SELECT * FROM $schemaQ.$tableQ
                    ORDER BY $cursorQ ASC
                    LIMIT ?
                    """.trimIndent()
                }
                val binder: (PreparedStatement, String?) -> Unit = { stmt, c ->
                    if (c != null) stmt.setString(1, c)
                }
                Triple(sql, binder, cursorColumn)
            }
            pkColumn != null -> {
                val pkQ = PgIdent.quote(pkColumn)
                val sql = if (cursor != null) {
                    """
                    SELECT * FROM $schemaQ.$tableQ
                    WHERE $pkQ::text > ?
                    ORDER BY $pkQ ASC
                    LIMIT ?
                    """.trimIndent()
                } else {
                    """
                    SELECT * FROM $schemaQ.$tableQ
                    ORDER BY $pkQ ASC
                    LIMIT ?
                    """.trimIndent()
                }
                val binder: (PreparedStatement, String?) -> Unit = { stmt, c ->
                    if (c != null) stmt.setString(1, c)
                }
                Triple(sql, binder, pkColumn)
            }
            else -> throw AdapterCapabilityNotSupportedException(
                "Table $tableName has neither a sync cursor nor a primary key — cannot fetch records",
            )
        }
    }

    /**
     * Converts a raw JDBC value into the shape carried by
     * [EntityAttributePrimitivePayload.value] (typealias for `Any?`):
     *  - `jsonb`/`json` PGobjects → parsed `Map<String,Any?>` / `List<Any?>`
     *  - `bytea` → `{"_bytea": "<base64>"}` object
     *  - everything else → passthrough (JDBC has already decoded to Kotlin types)
     */
    private fun convertValue(raw: Any?, pgType: String): Any? {
        if (raw == null) return null
        val normalized = pgType.lowercase()
        return when {
            normalized == "jsonb" || normalized == "json" -> parseJson(raw.toString())
            normalized == "bytea" || raw is ByteArray -> {
                val bytes = if (raw is ByteArray) raw else raw.toString().toByteArray()
                mapOf("_bytea" to Base64.getEncoder().encodeToString(bytes))
            }
            else -> raw
        }
    }

    private fun parseJson(text: String): Any? {
        return try {
            objectMapper.readValue(text, Any::class.java)
        } catch (_: Exception) {
            text
        }
    }
}
