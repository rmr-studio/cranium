package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Pool configuration for the Phase 3 Postgres adapter (PG-02).
 *
 * Discovered via `@ConfigurationPropertiesScan` declared on `CoreApplication`.
 * Values map to [riven.core.service.connector.pool.WorkspaceConnectionPoolManager]
 * HikariCP builder configuration. Defaults mirror the PROJECT.md decisions:
 * small pool per tenant, conservative timeouts, hard 5-minute statement cap.
 *
 * @property maxPoolSize Max connections per pooled `connectionId`. Two so a
 *   single workspace cannot starve a pod.
 * @property idleTimeoutMinutes Idle connections closed after this many minutes.
 * @property maxLifetimeMinutes Hard rotation ceiling per connection.
 * @property connectionTimeoutSeconds Wait this long before failing a `getConnection`.
 * @property statementTimeoutMillis Per-JDBC-connection `statement_timeout` cap
 *   (applied via `options=-c statement_timeout=...`).
 * @property defaultBatchSize Adapter fetchSize + per-call hard cap on requested
 *   `limit`; callers cannot over-request beyond this ceiling.
 * @property minimumIdle Minimum idle connections HikariCP keeps warm. Zero
 *   means idle connections may retire fully when the pool is quiet — safest
 *   default for per-tenant pools where most connections are cold.
 */
@ConfigurationProperties(prefix = "riven.connector.pool")
data class ConnectorPoolProperties(
    val maxPoolSize: Int = 2,
    val idleTimeoutMinutes: Long = 10,
    val maxLifetimeMinutes: Long = 30,
    val connectionTimeoutSeconds: Long = 10,
    val statementTimeoutMillis: Long = 300_000,
    val defaultBatchSize: Int = 5_000,
    val minimumIdle: Int = 0,
)
