package riven.core.service.catalog

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.enums.catalog.ManifestType
import riven.core.exceptions.NotFoundException
import riven.core.models.catalog.CatalogEntityTypeModel
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ManifestDetail
import riven.core.repository.catalog.*
import riven.core.repository.entity.EntityTypeRepository
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
    private val entityTypeRepository: EntityTypeRepository,
    private val logger: KLogger
) {

    // ------ Public Read Operations ------

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

    /**
     * Resolve the [ConnotationSignals] declared on the integration manifest entry that
     * corresponds to a workspace's entity type, if any.
     *
     * Lazy-resolves through [EntityTypeRepository] (entity domain) to find the workspace
     * entity type's `sourceManifestId` and `key`, then looks up the matching catalog row.
     * This introduces a cross-domain dependency from catalog -> entity which is intentional:
     * the connotation pipeline needs a single entry point keyed by workspace entity type id.
     *
     * Returns null when:
     * - The entity type does not exist.
     * - The entity type has no `sourceManifestId` (user-created -- not derived from a manifest).
     * - No catalog row matches the (manifestId, key) pair.
     * - The catalog row exists but the manifest entry omits `connotationSignals`.
     *
     * Callers should treat null as "no Tier 1 mapping configured -- leave SENTIMENT axis at NOT_APPLICABLE".
     */
    fun getConnotationSignalsForEntityType(entityTypeId: UUID): ConnotationSignals? {
        val entityType = entityTypeRepository.findById(entityTypeId).orElse(null) ?: return null
        val manifestId = entityType.sourceManifestId ?: return null
        val catalog = catalogEntityTypeRepository.findByManifestIdAndKey(manifestId, entityType.key) ?: return null
        return catalog.connotationSignals
    }

    // ------ Private Helpers ------

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
