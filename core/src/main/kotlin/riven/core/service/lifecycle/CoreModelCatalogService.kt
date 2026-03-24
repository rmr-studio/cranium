package riven.core.service.lifecycle

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import riven.core.lifecycle.CoreModelRegistry
import riven.core.service.catalog.ManifestUpsertService

/**
 * Populates the manifest catalog with core lifecycle model definitions at boot time.
 *
 * Runs on ApplicationReadyEvent alongside ManifestLoaderService (both synchronous on the
 * event thread, order depends on Spring bean ordering). Both paths converge at
 * ManifestUpsertService which is idempotent — content hash matching prevents duplicate
 * work regardless of execution order.
 *
 * Flow:
 *   1. CoreModelRegistry self-validates on first access (fail fast on broken definitions)
 *   2. For each model set: convert to ResolvedManifest → upsert to catalog
 *   3. Log summary
 *
 * ManifestUpsertService handles idempotency — re-running on the same definitions
 * with matching content hash skips child reconciliation.
 */
@Service
class CoreModelCatalogService(
    private val upsertService: ManifestUpsertService,
    private val logger: KLogger,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        val manifests = CoreModelRegistry.allResolvedManifests()
        for (manifest in manifests) {
            upsertService.upsertManifest(manifest)
            logger.info { "Core model set '${manifest.key}' loaded: ${manifest.entityTypes.size} entity types, ${manifest.relationships.size} relationships" }
        }

        val totalEntityTypes = manifests.sumOf { it.entityTypes.size }
        logger.info { "Core model catalog populated: ${manifests.size} model sets, $totalEntityTypes total entity types" }
    }
}
