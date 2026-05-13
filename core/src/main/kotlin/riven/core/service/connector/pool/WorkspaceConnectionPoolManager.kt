package cranium.core.service.connector.pool

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KLogger
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import cranium.core.configuration.properties.ConnectorPoolProperties
import cranium.core.models.connector.CredentialPayload
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One HikariCP pool per `connectionId`, cached for the lifetime of the JVM.
 *
 * Pool keying is **by connectionId, not by workspaceId** — the "Workspace"
 * prefix is historic (matches the 03-CONTEXT.md naming). A workspace may own
 * many connections; each gets its own pool.
 *
 * Thread-safe via [ConcurrentHashMap.computeIfAbsent]: concurrent callers
 * resolving the same `connectionId` race safely — only one pool is built, the
 * others receive the cached reference. Subsequent calls with **different**
 * credentials for the same `connectionId` are ignored (cached pool wins);
 * callers must [evict] first when credentials rotate.
 *
 * Lifecycle: [evict] and [evictAll] close pool-owned connections synchronously
 * (HikariCP drains and closes the underlying pool). [closeAll] is registered
 * via `@PreDestroy` so Spring shuts pools down cleanly on context close.
 *
 * Covers PG-02. Configuration is sourced from [ConnectorPoolProperties].
 */
@Service
@ConditionalOnProperty(prefix = "cranium.connector", name = ["enabled"], havingValue = "true")
class WorkspaceConnectionPoolManager(
    private val props: ConnectorPoolProperties,
    private val logger: KLogger,
) {
    private val pools = ConcurrentHashMap<UUID, HikariDataSource>()

    /**
     * Returns the cached pool for [connectionId], building one with [credentials]
     * on first access. Subsequent calls return the cached reference regardless
     * of the credentials argument — callers are responsible for [evict]ing when
     * credentials rotate.
     */
    fun getPool(connectionId: UUID, credentials: CredentialPayload): HikariDataSource =
        pools.computeIfAbsent(connectionId) {
            logger.info { "Building Hikari pool for connection=$connectionId" }
            buildPool(credentials)
        }

    /** True when [connectionId] has a cached pool. Convenience for tests + observability. */
    fun isPooled(connectionId: UUID): Boolean = pools.containsKey(connectionId)

    /** Closes and removes the pool for [connectionId]. No-op if not pooled. */
    fun evict(connectionId: UUID) {
        pools.remove(connectionId)?.also {
            logger.info { "Evicting Hikari pool for connection=$connectionId" }
            it.close()
        }
    }

    /** Closes and clears every pool. */
    fun evictAll() {
        val keys = pools.keys.toList()
        keys.forEach { evict(it) }
    }

    @PreDestroy
    fun closeAll() = evictAll()

    // ------ Private helpers ------

    /**
     * Builds a new [HikariDataSource] from [credentials]. The JDBC URL is
     * assembled from host/port/database/sslMode; `options=-c statement_timeout=...`
     * is installed as a connection property so every checked-out connection
     * inherits the hard 5-minute statement cap.
     */
    private fun buildPool(credentials: CredentialPayload): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${credentials.host}:${credentials.port}" +
                "/${credentials.database}?sslmode=${credentials.sslMode.value}"
            username = credentials.user
            password = credentials.password
            maximumPoolSize = props.maxPoolSize
            minimumIdle = props.minimumIdle
            idleTimeout = props.idleTimeoutMinutes.minutes.inWholeMilliseconds
            maxLifetime = props.maxLifetimeMinutes.minutes.inWholeMilliseconds
            connectionTimeout = props.connectionTimeoutSeconds.seconds.inWholeMilliseconds
            // Lazy init — pool construction must not fail when the remote DB is
            // unreachable at build time; HikariCP will surface the connection
            // error on the first `getConnection()` call instead.
            initializationFailTimeout = -1
            addDataSourceProperty("options", "-c statement_timeout=${props.statementTimeoutMillis}")
        }
        return HikariDataSource(config)
    }
}
