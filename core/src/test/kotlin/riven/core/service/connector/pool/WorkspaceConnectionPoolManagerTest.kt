package cranium.core.service.connector.pool

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import cranium.core.configuration.properties.ConnectorPoolProperties
import cranium.core.enums.connector.SslMode
import cranium.core.models.connector.CredentialPayload
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Pool-caching/eviction/config tests for [WorkspaceConnectionPoolManager] (Phase 3 PG-02).
 *
 * Hikari does not eagerly connect — [com.zaxxer.hikari.HikariDataSource] defers
 * actual socket creation until `getConnection()`. We can therefore build real
 * pools against fake credentials and assert on the configured `HikariConfig`
 * values without requiring a running Postgres.
 */
class WorkspaceConnectionPoolManagerTest {

    private val props = ConnectorPoolProperties()
    private val logger: io.github.oshai.kotlinlogging.KLogger = mock()

    private fun credentials(host: String = "localhost", user: String = "readonly") = CredentialPayload(
        host = host,
        port = 5432,
        database = "analytics",
        user = user,
        password = "pw",
        sslMode = SslMode.REQUIRE,
    )

    private fun manager() = WorkspaceConnectionPoolManager(props, logger)

    @Test
    fun getPoolReturnsCachedInstanceOnSubsequentCalls() {
        val mgr = manager()
        val id = UUID.randomUUID()

        val first = mgr.getPool(id, credentials())
        val second = mgr.getPool(id, credentials())

        try {
            assertThat(second).isSameAs(first)
            assertThat(mgr.isPooled(id)).isTrue()
        } finally {
            mgr.evictAll()
        }
    }

    @Test
    fun evictClosesPoolAndRemovesEntry() {
        val mgr = manager()
        val id = UUID.randomUUID()

        val first = mgr.getPool(id, credentials())
        mgr.evict(id)

        assertThat(mgr.isPooled(id)).isFalse()
        assertThat(first.isClosed).isTrue()

        val second = mgr.getPool(id, credentials())
        try {
            assertThat(second).isNotSameAs(first)
        } finally {
            mgr.evictAll()
        }
    }

    @Test
    fun evictAllClosesAllPools() {
        val mgr = manager()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()

        val p1 = mgr.getPool(id1, credentials())
        val p2 = mgr.getPool(id2, credentials())
        val p3 = mgr.getPool(id3, credentials())

        mgr.evictAll()

        assertThat(mgr.isPooled(id1)).isFalse()
        assertThat(mgr.isPooled(id2)).isFalse()
        assertThat(mgr.isPooled(id3)).isFalse()
        assertThat(p1.isClosed).isTrue()
        assertThat(p2.isClosed).isTrue()
        assertThat(p3.isClosed).isTrue()
    }

    @Test
    fun preDestroyClosesAllPools() {
        val mgr = manager()
        val id = UUID.randomUUID()
        val pool = mgr.getPool(id, credentials())

        mgr.closeAll()

        assertThat(pool.isClosed).isTrue()
        assertThat(mgr.isPooled(id)).isFalse()
    }

    @Test
    fun getPoolUsesProvidedCredentialsForFirstBuild() {
        val mgr = manager()
        val id = UUID.randomUUID()

        // First call wins.
        val pool = mgr.getPool(id, credentials(host = "first.example.com", user = "first-user"))
        try {
            assertThat(pool.jdbcUrl).contains("first.example.com")
            assertThat(pool.username).isEqualTo("first-user")

            // Second call with different creds is IGNORED (cache wins).
            val cached = mgr.getPool(id, credentials(host = "second.example.com", user = "second-user"))
            assertThat(cached).isSameAs(pool)
            assertThat(cached.jdbcUrl).contains("first.example.com")
            assertThat(cached.username).isEqualTo("first-user")
        } finally {
            mgr.evictAll()
        }
    }

    @Test
    fun poolConfiguresMaxPoolSize2IdleTimeout10mMaxLifetime30mStatementTimeout() {
        val mgr = manager()
        val id = UUID.randomUUID()
        val pool = mgr.getPool(id, credentials())

        try {
            assertThat(pool.maximumPoolSize).isEqualTo(2)
            assertThat(pool.idleTimeout).isEqualTo(10.minutes.inWholeMilliseconds)
            assertThat(pool.maxLifetime).isEqualTo(30.minutes.inWholeMilliseconds)
            assertThat(pool.connectionTimeout).isEqualTo(10.seconds.inWholeMilliseconds)

            // statement_timeout is applied via the `options` JDBC property so
            // every checked-out connection inherits the cap.
            val options = pool.dataSourceProperties.getProperty("options")
            assertThat(options).isEqualTo("-c statement_timeout=300000")
        } finally {
            mgr.evictAll()
        }
    }
}
