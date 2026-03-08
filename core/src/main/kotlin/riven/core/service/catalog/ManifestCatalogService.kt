package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.catalog.ManifestType
import riven.core.exceptions.NotFoundException
import riven.core.models.catalog.CatalogEntityTypeModel
import riven.core.models.catalog.BundleDetail
import riven.core.models.catalog.ManifestDetail
import riven.core.models.catalog.ManifestSummary
import riven.core.repository.catalog.*
import java.util.*

/**
 * Read-only query service for the manifest catalog.
 *
 * Provides the downstream-facing API for querying loaded manifests.
 * All queries exclude stale entries -- stale manifests are invisible to consumers.
 * No workspace scoping -- the catalog is global.
 */
@Service
class ManifestCatalogService(
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val catalogEntityTypeRepository: CatalogEntityTypeRepository,
    private val catalogRelationshipRepository: CatalogRelationshipRepository,
    private val catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository,
    private val catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository,
    private val catalogFieldMappingRepository: CatalogFieldMappingRepository,
    private val logger: KLogger
) {

    // ------ Public Read Operations ------

    /**
     * Returns lightweight summaries of all non-stale template manifests.
     */
    fun getAvailableTemplates(): List<ManifestSummary> =
        getManifestSummaries(ManifestType.TEMPLATE)

    /**
     * Returns lightweight summaries of all non-stale model manifests.
     */
    fun getAvailableModels(): List<ManifestSummary> =
        getManifestSummaries(ManifestType.MODEL)

    /** Returns all non-stale bundle manifests with their template key lists. */
    fun getAvailableBundles(): List<BundleDetail> {
        val manifests = manifestCatalogRepository.findByManifestTypeAndStaleFalse(ManifestType.BUNDLE)
        return manifests.map { it.toBundleDetail() }
    }

    /**
     * Returns a bundle detail by key.
     *
     * @throws NotFoundException if the bundle key doesn't exist or is stale
     */
    fun getBundleByKey(key: String): BundleDetail {
        require(key.isNotBlank()) { "Bundle key must not be blank" }
        val catalog = manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse(key, ManifestType.BUNDLE)
            ?: throw NotFoundException("Bundle not found: $key")
        return catalog.toBundleDetail()
    }

    /**
     * Returns a fully hydrated manifest detail including entity types (with semantic metadata),
     * relationships (with target rules), and field mappings.
     *
     * @throws NotFoundException if the manifest key doesn't exist or is stale
     */
    fun getManifestByKey(key: String, manifestType: ManifestType): ManifestDetail {
        val catalog = manifestCatalogRepository.findByKeyAndManifestTypeAndStaleFalse(key, manifestType)
            ?: throw NotFoundException("Manifest not found: $key (type=$manifestType)")
        val manifestId = catalog.id!!

        val entityTypes = catalogEntityTypeRepository.findByManifestId(manifestId)
        val entityTypeIds = entityTypes.mapNotNull { it.id }
        val semanticsByEntityType = catalogSemanticMetadataRepository
            .findByCatalogEntityTypeIdIn(entityTypeIds)
            .groupBy { it.catalogEntityTypeId }

        val relationships = catalogRelationshipRepository.findByManifestId(manifestId)
        val relationshipIds = relationships.mapNotNull { it.id }
        val targetRulesByRelationship = catalogRelationshipTargetRuleRepository
            .findByCatalogRelationshipIdIn(relationshipIds)
            .groupBy { it.catalogRelationshipId }

        val fieldMappings = catalogFieldMappingRepository.findByManifestId(manifestId)

        return catalog.toDetail(
            entityTypes = entityTypes.map { et ->
                val metadata = semanticsByEntityType[et.id]?.map { it.toModel() } ?: emptyList()
                et.toModel(metadata)
            },
            relationships = relationships.map { rel ->
                val rules = targetRulesByRelationship[rel.id]?.map { it.toModel() } ?: emptyList()
                rel.toModel(rules)
            },
            fieldMappings = fieldMappings.map { it.toModel() }
        )
    }

    /**
     * Returns entity type definitions for a specific manifest, with semantic metadata batch-loaded.
     *
     * @throws NotFoundException if the manifest ID doesn't exist
     */
    fun getEntityTypesForManifest(manifestId: UUID): List<CatalogEntityTypeModel> {
        val manifest = manifestCatalogRepository.findById(manifestId)
            .orElseThrow { NotFoundException("Manifest not found: $manifestId") }
        if (manifest.stale) {
            throw NotFoundException("Manifest not found: $manifestId")
        }

        val entityTypes = catalogEntityTypeRepository.findByManifestId(manifestId)
        return hydrateEntityTypes(entityTypes)
    }

    // ------ Private Helpers ------

    private fun getManifestSummaries(manifestType: ManifestType): List<ManifestSummary> {
        val manifests = manifestCatalogRepository.findByManifestTypeAndStaleFalse(manifestType)
        if (manifests.isEmpty()) return emptyList()

        return manifests.map { manifest ->
            val count = catalogEntityTypeRepository.findByManifestId(manifest.id!!).size
            manifest.toSummary(count)
        }
    }

    private fun hydrateEntityTypes(
        entityTypes: List<riven.core.entity.catalog.CatalogEntityTypeEntity>
    ): List<CatalogEntityTypeModel> {
        val entityTypeIds = entityTypes.mapNotNull { it.id }
        val semanticsByEntityType = catalogSemanticMetadataRepository
            .findByCatalogEntityTypeIdIn(entityTypeIds)
            .groupBy { it.catalogEntityTypeId }

        return entityTypes.map { et ->
            val metadata = semanticsByEntityType[et.id]?.map { it.toModel() } ?: emptyList()
            et.toModel(metadata)
        }
    }
}
