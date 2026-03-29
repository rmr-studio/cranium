package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import riven.core.configuration.properties.ManifestConfigurationProperties
import riven.core.enums.catalog.ManifestType
import riven.core.models.catalog.ScannedManifest
import riven.core.repository.catalog.ManifestCatalogRepository
import java.time.Instant

/**
 * Orchestrates the full manifest loading pipeline on application startup.
 * Coordinates scan -> resolve -> upsert with post-load stale reconciliation.
 * Does NOT use @Transactional on the main method -- each manifest is
 * upserted in its own transaction via ManifestUpsertService.
 */
@Service
class ManifestLoaderService(
    private val scannerService: ManifestScannerService,
    private val resolverService: ManifestResolverService,
    private val upsertService: ManifestUpsertService,
    private val reconciliationService: ManifestReconciliationService,
    private val integrationDefinitionStaleSyncService: IntegrationDefinitionStaleSyncService,
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val healthIndicator: ManifestCatalogHealthIndicator,
    private val manifestProperties: ManifestConfigurationProperties,
    private val logger: KLogger
) {

    /** Triggers the full manifest load pipeline asynchronously after application startup. */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady(event: ApplicationReadyEvent) {
        if (!manifestProperties.autoLoad) return
        Thread({
            healthIndicator.loadState = ManifestCatalogHealthIndicator.LoadState.LOADING
            try {
                loadAllManifests()
                healthIndicator.loadState = ManifestCatalogHealthIndicator.LoadState.LOADED
                healthIndicator.lastLoadedAt = Instant.now()
            } catch (e: Exception) {
                healthIndicator.loadState = ManifestCatalogHealthIndicator.LoadState.FAILED
                healthIndicator.lastError = e.message
                logger.error(e) { "Manifest catalog load failed" }
            }
        }, "manifest-loader").start()
    }

    // ------ Core Orchestration ------

    /**
     * Loads integration manifests from classpath: scans and upserts integrations,
     * reconciles stale entries based on what was successfully loaded, syncs integration definitions,
     * and logs a summary. Core model manifests (templates) are loaded separately by CoreModelCatalogService.
     */
    fun loadAllManifests() {
        val seenManifests = mutableSetOf<Pair<String, ManifestType>>()
        val scannedIntegrations = scannerService.scanIntegrations()

        val result = loadIntegrations(scannedIntegrations, seenManifests)

        reconcileStaleEntries(scannedIntegrations.size, seenManifests)
        integrationDefinitionStaleSyncService.syncStaleFlags()

        val staleCount = manifestCatalogRepository.findByStaleTrue().size
        logger.info { "Manifest load complete: ${result.loaded} integrations loaded. $staleCount stale. ${result.skipped} skipped." }
    }

    // ------ Phase Loaders ------

    private data class PhaseResult(val loaded: Int, val skipped: Int)

    private fun loadIntegrations(
        scannedIntegrations: List<ScannedManifest>,
        seenManifests: MutableSet<Pair<String, ManifestType>>
    ): PhaseResult {
        var loaded = 0
        var skipped = 0

        for (scanned in scannedIntegrations) {
            try {
                val resolved = resolverService.resolveManifest(scanned)
                upsertService.upsertManifest(resolved)
                if (!resolved.stale) {
                    loaded++
                    seenManifests.add(scanned.key to scanned.type)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load integration manifest: ${scanned.key}" }
                skipped++
            }
        }

        return PhaseResult(loaded, skipped)
    }

    // ------ Post-Load Reconciliation ------

    private fun reconcileStaleEntries(
        totalScanned: Int,
        seenManifests: Set<Pair<String, ManifestType>>
    ) {
        if (totalScanned > 0) {
            reconciliationService.reconcileStaleEntries(seenManifests)
        } else {
            logger.warn { "No manifests found on classpath — skipping stale reconciliation" }
        }
    }
}
