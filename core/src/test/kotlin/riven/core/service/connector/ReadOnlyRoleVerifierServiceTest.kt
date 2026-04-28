package riven.core.service.connector

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.exceptions.connector.ReadOnlyVerificationException
import java.net.InetAddress
import java.sql.DriverManager
import java.util.Properties

/**
 * Integration tests for [ReadOnlyRoleVerifierService] — SEC-03 (read-only
 * role enforcement on connect). Uses a real Postgres via Testcontainers so
 * `pg_roles`, `has_table_privilege(...)`, `has_schema_privilege(...)`, and
 * SQLState 42501 behave exactly as production will.
 *
 * Role fixtures created per test (dropped + recreated via @BeforeEach so a
 * failing earlier test can't poison a later one):
 *  - `ro_user` — SELECT-only on public, no CREATE on public, no superuser.
 *  - `rw_user` — has INSERT on public.sample plus CREATE on schema public.
 *  - `postgres` (the Testcontainers superuser) — used to verify the
 *    superuser-attributes check triggers.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReadOnlyRoleVerifierServiceTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("riven_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }
    }

    private val logger: KLogger = mock()
    private val service = ReadOnlyRoleVerifierService(logger)

    @BeforeEach
    fun setupRoles() {
        // Drop any leftover probe tables first so an earlier failed test
        // can't taint this run. Then revoke privileges owned by ro_user /
        // rw_user before DROP ROLE — Postgres will otherwise reject the drop
        // with "role cannot be dropped because some objects depend on it".
        adminExecEach(
            "DROP TABLE IF EXISTS public.sample CASCADE",

            // Idempotent drop of ro_user (may not yet exist on first run).
            "DO \$do\$ BEGIN IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ro_user') THEN " +
                "EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM ro_user'; " +
                "EXECUTE 'REVOKE ALL ON SCHEMA public FROM ro_user'; " +
                "EXECUTE 'REVOKE ALL ON DATABASE riven_test FROM ro_user'; " +
                "EXECUTE 'DROP OWNED BY ro_user'; " +
                "EXECUTE 'DROP ROLE ro_user'; END IF; END \$do\$",

            "DO \$do\$ BEGIN IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rw_user') THEN " +
                "EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM rw_user'; " +
                "EXECUTE 'REVOKE ALL ON SCHEMA public FROM rw_user'; " +
                "EXECUTE 'REVOKE ALL ON DATABASE riven_test FROM rw_user'; " +
                "EXECUTE 'DROP OWNED BY rw_user'; " +
                "EXECUTE 'DROP ROLE rw_user'; END IF; END \$do\$",

            "CREATE ROLE ro_user LOGIN PASSWORD 'ro_pw'",
            "GRANT CONNECT ON DATABASE riven_test TO ro_user",
            "GRANT USAGE ON SCHEMA public TO ro_user",
            "REVOKE CREATE ON SCHEMA public FROM ro_user",
            "GRANT SELECT ON ALL TABLES IN SCHEMA public TO ro_user",

            "CREATE ROLE rw_user LOGIN PASSWORD 'rw_pw'",
            "GRANT CONNECT ON DATABASE riven_test TO rw_user",
            "GRANT USAGE, CREATE ON SCHEMA public TO rw_user",
            "CREATE TABLE public.sample (id int)",
            "GRANT INSERT, SELECT ON public.sample TO rw_user",
        )
    }

    private fun adminExecEach(vararg statements: String) {
        // Testcontainers' createConnection("") returns a superuser connection.
        // Execute each statement separately to avoid fragile ';' splitting
        // (PL/pgSQL DO blocks contain internal semicolons).
        postgres.createConnection("").use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                for (sql in statements) stmt.execute(sql)
            }
        }
    }

    private fun resolvedIp(): InetAddress = InetAddress.getByName(postgres.host)

    // ---------------- happy path ----------------

    @Test
    fun `accepts genuine read-only role`() {
        // Must not throw
        service.verify(
            host = "localhost",
            resolvedIp = resolvedIp(),
            port = postgres.firstMappedPort,
            database = postgres.databaseName,
            user = "ro_user",
            password = "ro_pw",
            sslMode = "disable",
        )
    }

    @Test
    fun `SAVEPOINT probe leaves no residue in public schema`() {
        service.verify(
            host = "localhost",
            resolvedIp = resolvedIp(),
            port = postgres.firstMappedPort,
            database = postgres.databaseName,
            user = "ro_user",
            password = "ro_pw",
            sslMode = "disable",
        )
        // After verify, confirm no __riven_ro_probe_* table exists.
        postgres.createConnection("").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery(
                    "SELECT COUNT(*) FROM pg_tables WHERE tablename LIKE '__riven_ro_probe_%'"
                ).use { rs ->
                    rs.next()
                    assertThat(rs.getInt(1)).isEqualTo(0)
                }
            }
        }
    }

    // ---------------- rejection paths ----------------

    @Test
    fun `rejects superuser role`() {
        val ex = assertThrows<ReadOnlyVerificationException> {
            service.verify(
                host = "localhost",
                resolvedIp = resolvedIp(),
                port = postgres.firstMappedPort,
                database = postgres.databaseName,
                user = postgres.username,  // 'test' — superuser in Testcontainers
                password = postgres.password,
                sslMode = "disable",
            )
        }
        assertThat(ex.message).contains("superuser")
    }

    @Test
    fun `rejects role with INSERT privileges on a public table`() {
        val ex = assertThrows<ReadOnlyVerificationException> {
            service.verify(
                host = "localhost",
                resolvedIp = resolvedIp(),
                port = postgres.firstMappedPort,
                database = postgres.databaseName,
                user = "rw_user",
                password = "rw_pw",
                sslMode = "disable",
            )
        }
        assertThat(ex.message)
            .contains("write privileges")
            .contains("read-only role required")
        // Must NOT leak table names — only a count.
        assertThat(ex.message).doesNotContain("sample")
    }

    // ---------------- failure surface sanitisation ----------------

    @Test
    fun `JDBC connection failure messages are sanitised of jdbc URLs`() {
        val ex = assertThrows<ReadOnlyVerificationException> {
            service.verify(
                host = "localhost",
                resolvedIp = resolvedIp(),
                port = postgres.firstMappedPort,
                database = postgres.databaseName,
                user = "ro_user",
                password = "WRONG_PASSWORD",  // forces FATAL: password authentication failed
                sslMode = "disable",
            )
        }
        assertThat(ex.message).doesNotContain("jdbc:postgresql://")
    }

    // ---------------- contract check: uses DriverManager, not HikariCP ----------------

    @Test
    fun `service does not reference HikariCP or pooled DataSource`() {
        // Cheap reflective smoke-check: scan declared fields and methods for
        // any HikariCP / javax.sql.DataSource references. We want short-lived
        // DriverManager connections, never pooled.
        val serviceClass = ReadOnlyRoleVerifierService::class.java
        val banned = listOf("Hikari", "DataSource", "ConnectionPool")
        val text = serviceClass.declaredFields.joinToString { it.type.name } +
            "|" + serviceClass.declaredMethods.joinToString { m -> m.parameterTypes.joinToString { it.name } }
        banned.forEach { needle ->
            assertThat(text).doesNotContain(needle)
        }
        // Positive check: DriverManager reachable and usable (canary — the
        // actual verify() calls above already prove the path works).
        DriverManager.getConnection(postgres.jdbcUrl, Properties().apply {
            setProperty("user", postgres.username); setProperty("password", postgres.password)
        }).close()
    }
}
