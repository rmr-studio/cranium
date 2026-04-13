package riven.core.service.connector.pool

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Phase 3 Wave-0 placeholder for WorkspaceConnectionPoolManager
 * (owned by plan 03-02, covers PG-02 pool caching/eviction).
 * Downstream plan removes @Disabled and replaces placeholder() with real assertions.
 */
@Disabled("populated by plan 03-02")
class WorkspaceConnectionPoolManagerTest {

    @Test fun getPoolReturnsCachedInstanceOnSubsequentCalls() = placeholder()
    @Test fun evictClosesPoolAndRemovesEntry() = placeholder()
    @Test fun evictAllClosesAllPools() = placeholder()
    @Test fun preDestroyClosesAllPools() = placeholder()
    @Test fun getPoolUsesProvidedCredentialsForFirstBuild() = placeholder()
    @Test fun poolConfiguresMaxPoolSize2_idleTimeout10m_maxLifetime30m_statementTimeout() = placeholder()

    private fun placeholder() {}
}
