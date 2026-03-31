package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.enums.catalog.ManifestType
import riven.core.repository.catalog.ManifestCatalogRepository

/**
 * Post-load reconciliation: marks catalog entries stale if they were NOT
 * seen during the current load cycle, and un-stales entries that were seen.
 *
 * This replaces the old "mark all stale up front" approach which could leave
 * the entire catalog dark if the app crashed mid-load.
 */
@Service
class ManifestReconciliationService(
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val logger: KLogger
) {

    /**
     * Reconciles stale flags after a successful load cycle.
     * Only operates on catalog entries whose [ManifestType] is represented in [reconciledTypes] —
     * manifest types loaded by other services (e.g. core model templates) are not touched.
     * Entries whose (key, type) pair is in [seenManifests] are marked non-stale;
     * entries of a reconciled type not in the set are marked stale.
     */
    @Transactional
    fun reconcileStaleEntries(
        seenManifests: Set<Pair<String, ManifestType>>,
        reconciledTypes: Set<ManifestType>
    ) {
        val allEntries = manifestCatalogRepository.findAll()
        var markedStale = 0
        var unmarkedStale = 0

        for (entry in allEntries) {
            if (entry.manifestType !in reconciledTypes) continue

            val shouldBeStale = (entry.key to entry.manifestType) !in seenManifests
            if (entry.stale != shouldBeStale) {
                entry.stale = shouldBeStale
                manifestCatalogRepository.save(entry)
                if (shouldBeStale) markedStale++ else unmarkedStale++
            }
        }

        if (markedStale > 0) {
            logger.info { "Reconciliation marked $markedStale catalog entries as stale" }
        }
        if (unmarkedStale > 0) {
            logger.info { "Reconciliation un-staled $unmarkedStale catalog entries" }
        }
    }
}
