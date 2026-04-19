package riven.core.service.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.Status
import java.time.Instant


class ManifestCatalogHealthIndicatorTest {

    private val indicator = ManifestCatalogHealthIndicator()

    @Test
    fun `reports UP with PENDING state by default`() {
        val health = buildHealth()

        assertEquals(Status.UP, health.status)
        assertEquals("PENDING", health.details["loadState"])
    }

    @Test
    fun `reports UP with LOADING state`() {
        indicator.loadState = ManifestCatalogHealthIndicator.LoadState.LOADING

        val health = buildHealth()

        assertEquals(Status.UP, health.status)
        assertEquals("LOADING", health.details["loadState"])
    }

    @Test
    fun `reports UP with LOADED state and lastLoadedAt`() {
        indicator.loadState = ManifestCatalogHealthIndicator.LoadState.LOADED
        indicator.lastLoadedAt = Instant.parse("2026-01-01T00:00:00Z")

        val health = buildHealth()

        assertEquals(Status.UP, health.status)
        assertEquals("LOADED", health.details["loadState"])
        assertNotNull(health.details["lastLoadedAt"])
    }

    @Test
    fun `reports DOWN with FAILED state and error detail`() {
        indicator.loadState = ManifestCatalogHealthIndicator.LoadState.FAILED
        indicator.lastError = "Connection refused"

        val health = buildHealth()

        assertEquals(Status.DOWN, health.status)
        assertEquals("FAILED", health.details["loadState"])
        assertEquals("Connection refused", health.details["error"])
    }

    private fun buildHealth(): Health = indicator.health()
}
