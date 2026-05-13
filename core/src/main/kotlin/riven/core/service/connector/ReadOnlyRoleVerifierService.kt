package cranium.core.service.connector

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import cranium.core.exceptions.connector.ReadOnlyVerificationException
import java.net.Inet6Address
import java.net.InetAddress
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID

/**
 * Probes a candidate Postgres role to confirm it is genuinely read-only.
 * Implements SEC-03.
 *
 * Three-step verification, all within a single short-lived
 * [DriverManager.getConnection] — **not HikariCP** (we don't want the
 * unverified role ever entering the pool):
 *
 *  1. **Superuser attribute check** — `SELECT rolsuper, rolcreatedb,
 *     rolcreaterole FROM pg_roles WHERE rolname = current_user`. Any true →
 *     reject.
 *  2. **Write-privilege sweep** — count user-accessible tables where the role
 *     has INSERT/UPDATE/DELETE. Any positive count → reject, reporting the
 *     count only (never table names).
 *  3. **SAVEPOINT probe** — attempt `CREATE TABLE __cranium_ro_probe_<uuid>`
 *     inside a savepoint. If it succeeds, the role can mutate schema → reject
 *     and roll back. If it fails with SQLState `42501` (insufficient
 *     privilege), that's exactly the behaviour we require.
 *
 * Connection identity: the caller passes [resolvedIp] (from
 * [SsrfValidatorService.validateAndResolve]) so we connect by IP literal,
 * defeating DNS rebinding between validation and probe. The original
 * hostname is still forwarded via `sslServerHostname` so TLS SNI /
 * certificate verification target the real hostname.
 */
@Service
class ReadOnlyRoleVerifierService(
    private val logger: KLogger,
) {

    /**
     * @throws ReadOnlyVerificationException when the role is not read-only,
     *   cannot connect, or any JDBC error occurs. Error messages are
     *   sanitised to strip embedded JDBC URLs before logging or propagation.
     */
    fun verify(
        host: String,
        resolvedIp: InetAddress,
        port: Int,
        database: String,
        user: String,
        password: String,
        sslMode: String,
    ) {
        val jdbcUrl = buildJdbcUrl(resolvedIp, port, database, sslMode, host)
        val props = Properties().apply {
            setProperty("user", user)
            setProperty("password", password)
            // pgjdbc timeouts (seconds). Prevent a hostile/slow server from
            // hanging the verifier thread indefinitely.
            setProperty("connectTimeout", CONNECT_TIMEOUT_SECONDS.toString())
            setProperty("socketTimeout", SOCKET_TIMEOUT_SECONDS.toString())
            setProperty("loginTimeout", CONNECT_TIMEOUT_SECONDS.toString())
        }

        try {
            DriverManager.setLoginTimeout(CONNECT_TIMEOUT_SECONDS)
            DriverManager.getConnection(jdbcUrl, props).use { conn ->
                conn.autoCommit = false // SAVEPOINT requires an active txn
                applyStatementTimeout(conn)
                checkSuperuserAttributes(conn)
                checkWritePrivileges(conn)
                checkSavepointProbe(conn)
                conn.rollback() // no side effects, ever
            }
        } catch (e: ReadOnlyVerificationException) {
            throw e
        } catch (e: SQLException) {
            val sanitized = sanitize(e.message)
            logger.warn { "RO verification JDBC failure: $sanitized" }
            throw ReadOnlyVerificationException("Database connection failed: $sanitized", e)
        }
    }

    // ---------- URL + checks ----------

    private fun buildJdbcUrl(
        ip: InetAddress,
        port: Int,
        database: String,
        sslMode: String,
        hostForSni: String,
    ): String {
        // Bracket IPv6 literals per RFC 3986 / pgjdbc requirements.
        val ipHost = if (ip is Inet6Address) "[${ip.hostAddress}]" else ip.hostAddress
        return "jdbc:postgresql://$ipHost:$port/$database" +
            "?sslmode=$sslMode" +
            "&sslServerHostname=$hostForSni"
    }

    private fun checkSuperuserAttributes(conn: Connection) {
        conn.prepareStatement(
            "SELECT rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname = current_user"
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val isSuper = rs.getBoolean(1)
                    val canCreateDb = rs.getBoolean(2)
                    val canCreateRole = rs.getBoolean(3)
                    if (isSuper || canCreateDb || canCreateRole) {
                        throw ReadOnlyVerificationException(
                            "Role has superuser/createdb/createrole attributes — read-only role required."
                        )
                    }
                }
            }
        }
    }

    private fun checkWritePrivileges(conn: Connection) {
        val sql = """
            SELECT COUNT(*)
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind IN ('r', 'p')
              AND n.nspname NOT IN ('information_schema', 'pg_catalog')
              AND has_schema_privilege(current_user, n.nspname, 'USAGE')
              AND (has_table_privilege(current_user, c.oid, 'INSERT')
                OR has_table_privilege(current_user, c.oid, 'UPDATE')
                OR has_table_privilege(current_user, c.oid, 'DELETE'))
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val count = rs.getInt(1)
                    if (count > 0) {
                        throw ReadOnlyVerificationException(
                            "Role has write privileges on $count table(s) — read-only role required."
                        )
                    }
                }
            }
        }
    }

    private fun checkSavepointProbe(conn: Connection) {
        val probeName = "__cranium_ro_probe_" + UUID.randomUUID().toString().replace("-", "")
        val savepoint = conn.setSavepoint("ro_check")
        try {
            conn.prepareStatement("CREATE TABLE $probeName (v int)").use { it.execute() }
            // Reaching here means CREATE succeeded — the role can mutate the
            // schema, which is not read-only.
            throw ReadOnlyVerificationException(
                "Role can CREATE tables — read-only role required."
            )
        } catch (e: SQLException) {
            if (e.sqlState == "42501") {
                // Expected: insufficient_privilege — this is a pass signal.
                return
            }
            // Re-wrap as ReadOnlyVerificationException with sanitised message
            // so we don't leak jdbc URLs in unexpected SQLExceptions either.
            throw ReadOnlyVerificationException(
                "SAVEPOINT probe failed: ${sanitize(e.message)}",
                e,
            )
        } finally {
            try {
                conn.rollback(savepoint)
            } catch (_: SQLException) {
                // best-effort — connection is about to be closed anyway
            }
        }
    }

    private fun applyStatementTimeout(conn: Connection) {
        // SET LOCAL binds the timeout to the current transaction, rolled back
        // with the probe. Prevents a hung probe SQL from blocking the thread.
        conn.createStatement().use { stmt ->
            stmt.execute("SET LOCAL statement_timeout = '${STATEMENT_TIMEOUT_MS}ms'")
        }
    }

    private fun sanitize(message: String?): String =
        message?.replace(Regex("(jdbc:)?postgresql://\\S+"), "[REDACTED_JDBC_URL]") ?: "unknown"

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 5
        private const val SOCKET_TIMEOUT_SECONDS = 10
        private const val STATEMENT_TIMEOUT_MS = 5_000
    }
}
