package cranium.core.service.connector.mapping

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import cranium.core.models.connector.CredentialPayload
import cranium.core.service.connector.pool.WorkspaceConnectionPoolManager
import java.util.UUID

/**
 * Probe `pg_indexes` to decide whether a given column on a given table has any
 * supporting index (single-column heuristic — sufficient for the cursor-index
 * warning use case per 03-CONTEXT.md).
 *
 * Uses the cached HikariCP pool via [WorkspaceConnectionPoolManager]; the
 * probe is a single `SELECT 1 ... LIMIT 1` and is cheap enough to run on
 * every GET /schema and every POST /mapping.
 */
@Component
@ConditionalOnProperty(prefix = "cranium.connector", name = ["enabled"], havingValue = "true")
class CursorIndexProbe(
    private val poolManager: WorkspaceConnectionPoolManager,
    @Suppress("unused") private val logger: KLogger,
) {

    /**
     * Returns `true` iff `pg_indexes` has at least one row whose `indexdef`
     * references `columnName` on `(schema.tableName)`.
     */
    fun isIndexed(
        connectionId: UUID,
        credentials: CredentialPayload,
        schema: String,
        tableName: String,
        columnName: String,
    ): Boolean {
        val dataSource = poolManager.getPool(connectionId, credentials)
        val sql = """
            SELECT 1
            FROM pg_indexes
            WHERE schemaname = ?
              AND tablename = ?
              AND indexdef ILIKE ?
            LIMIT 1
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, schema)
                stmt.setString(2, tableName)
                stmt.setString(3, "%($columnName)%")
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }
}
