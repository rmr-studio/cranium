package riven.core.service.catalog

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
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
    private val logger: KLogger
) {

    /** Triggers the full manifest load pipeline asynchronously after application startup. */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady(event: ApplicationReadyEvent) {
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
     * Loads all manifests from classpath: scans and upserts models -> templates -> integrations,
     * reconciles stale entries based on what was successfully loaded, syncs integration definitions,
     * and logs a summary.
     */
    fun loadAllManifests() {
        val seenManifests = mutableSetOf<Pair<String, ManifestType>>()

        val scannedModels = scannerService.scanModels()
        val scannedTemplates = scannerService.scanTemplates()
        val scannedIntegrations = scannerService.scanIntegrations()

        val (modelIndex, modelsResult) = loadModels(scannedModels, seenManifests)
        val templatesResult = loadTemplates(scannedTemplates, modelIndex, seenManifests)
        val integrationsResult = loadIntegrations(scannedIntegrations, seenManifests)

        reconcileStaleEntries(scannedModels.size + scannedTemplates.size + scannedIntegrations.size, seenManifests)
        integrationDefinitionStaleSyncService.syncStaleFlags()

        val totalSkipped = modelsResult.skipped + templatesResult.skipped + integrationsResult.skipped
        val staleCount = manifestCatalogRepository.findByStaleTrue().size
        logger.info { "Manifest load complete: ${modelsResult.loaded} models, ${templatesResult.loaded} templates, ${integrationsResult.loaded} integrations loaded. $staleCount stale. $totalSkipped skipped." }
    }

    // ------ Phase Loaders ------

    private data class PhaseResult(val loaded: Int, val skipped: Int)

    private fun loadModels(
        scannedModels: List<ScannedManifest>,
        seenManifests: MutableSet<Pair<String, ManifestType>>
    ): Pair<Map<String, JsonNode>, PhaseResult> {
        val modelIndex = mutableMapOf<String, JsonNode>()
        var loaded = 0
        var skipped = 0

        for (scanned in scannedModels) {
            try {
                val resolved = resolverService.resolveManifest(scanned, emptyMap())
                upsertService.upsertManifest(resolved)
                if (!resolved.stale) {
                    modelIndex[scanned.key] = scanned.json
                    loaded++
                    seenManifests.add(scanned.key to scanned.type)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load model manifest: ${scanned.key}" }
                skipped++
            }
        }

        return modelIndex to PhaseResult(loaded, skipped)
    }

    private fun loadTemplates(
        scannedTemplates: List<ScannedManifest>,
        modelIndex: Map<String, JsonNode>,
        seenManifests: MutableSet<Pair<String, ManifestType>>
    ): PhaseResult {
        var loaded = 0
        var skipped = 0

        for (scanned in scannedTemplates) {
            try {
                val resolved = resolverService.resolveManifest(scanned, modelIndex)
                upsertService.upsertManifest(resolved)
                if (!resolved.stale) {
                    loaded++
                    seenManifests.add(scanned.key to scanned.type)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to load template manifest: ${scanned.key}" }
                skipped++
            }
        }

        return PhaseResult(loaded, skipped)
    }

    private fun loadIntegrations(
        scannedIntegrations: List<ScannedManifest>,
        seenManifests: MutableSet<Pair<String, ManifestType>>
    ): PhaseResult {
        var loaded = 0
        var skipped = 0

        for (scanned in scannedIntegrations) {
            try {
                val resolved = resolverService.resolveManifest(scanned, emptyMap())
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
