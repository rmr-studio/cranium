package riven.core.service.catalog

import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Health indicator for the manifest catalog load pipeline.
 * Reports the current load state so that /actuator/health reflects
 * whether the catalog has been successfully populated.
 */
@Component
class ManifestCatalogHealthIndicator : AbstractHealthIndicator() {

    enum class LoadState { PENDING, LOADING, LOADED, FAILED }

    @Volatile
    var loadState: LoadState = LoadState.PENDING

    @Volatile
    var lastError: String? = null

    @Volatile
    var lastLoadedAt: Instant? = null

    override fun doHealthCheck(builder: Health.Builder) {
        builder.withDetail("loadState", loadState.name)
        lastLoadedAt?.let { builder.withDetail("lastLoadedAt", it.toString()) }
        when (loadState) {
            LoadState.PENDING, LoadState.LOADING -> builder.up()
            LoadState.LOADED -> builder.up()
            LoadState.FAILED -> builder.down().withDetail("error", lastError ?: "unknown")
        }
    }
}
