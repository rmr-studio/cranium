package riven.core.service.lifecycle

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import riven.core.models.core.CoreModelRegistry
import riven.core.service.catalog.ManifestCatalogHealthIndicator
import riven.core.service.catalog.ManifestUpsertService

/**
 * Populates the manifest catalog with core lifecycle model definitions at boot time.
 *
 * Runs on ApplicationReadyEvent alongside ManifestLoaderService (both synchronous on the
 * event thread, order depends on Spring bean ordering). Both paths converge at
 * ManifestUpsertService which is idempotent — content hash matching prevents duplicate
 * work regardless of execution order.
 *
 * Error handling: individual model set failures are logged and skipped. The health indicator
 * reflects partial failures. A model set that fails transiently will succeed on next restart
 * due to content hash idempotency. Deterministic failures (malformed definitions) require a
 * code fix but do not crash the application.
 */
@Service
class CoreModelCatalogService(
    private val upsertService: ManifestUpsertService,
    private val healthIndicator: ManifestCatalogHealthIndicator,
    private val logger: KLogger,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        try {
            val manifests = CoreModelRegistry.allResolvedManifests()
            var loaded = 0
            var failed = 0

            for (manifest in manifests) {
                try {
                    upsertService.upsertManifest(manifest)
                    loaded++
                    logger.info { "Core model set '${manifest.key}' loaded: ${manifest.entityTypes.size} entity types, ${manifest.relationships.size} relationships" }
                } catch (e: Exception) {
                    failed++
                    logger.error(e) { "Failed to load core model set '${manifest.key}'" }
                }
            }

            if (failed > 0) {
                healthIndicator.loadState = ManifestCatalogHealthIndicator.LoadState.FAILED
                healthIndicator.lastError = "$failed/${manifests.size} core model sets failed to load"
                logger.error { "Core model catalog partially loaded: $loaded succeeded, $failed failed" }
            } else {
                val totalEntityTypes = manifests.sumOf { it.entityTypes.size }
                logger.info { "Core model catalog populated: ${manifests.size} model sets, $totalEntityTypes total entity types" }
            }
        } catch (e: Exception) {
            healthIndicator.loadState = ManifestCatalogHealthIndicator.LoadState.FAILED
            healthIndicator.lastError = "Core model registry validation failed: ${e.message}"
            logger.error(e) { "Core model registry validation failed — no core models loaded" }
        }
    }
}
