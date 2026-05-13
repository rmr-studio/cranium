package cranium.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import cranium.core.enums.catalog.ManifestType
import cranium.core.repository.catalog.ManifestCatalogRepository
import cranium.core.repository.integration.IntegrationDefinitionRepository

/**
 * Syncs the `stale` flag on integration_definitions to match
 * the corresponding manifest_catalog entry after a load cycle.
 *
 * Extracted from ManifestLoaderService to ensure @Transactional
 * is honored via Spring AOP proxy (avoids self-invocation bypass).
 */
@Service
class IntegrationDefinitionStaleSyncService(
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val integrationDefinitionRepository: IntegrationDefinitionRepository,
    private val logger: KLogger
) {

    /**
     * Propagates stale flags from integration catalog entries to their
     * corresponding integration_definitions rows.
     */
    @Transactional
    fun syncStaleFlags() {
        val integrationCatalogEntries = manifestCatalogRepository.findByManifestType(ManifestType.INTEGRATION)
        for (entry in integrationCatalogEntries) {
            val definition = integrationDefinitionRepository.findBySlug(entry.key) ?: continue
            if (definition.stale != entry.stale) {
                definition.stale = entry.stale
                integrationDefinitionRepository.save(definition)
            }
        }
    }
}
