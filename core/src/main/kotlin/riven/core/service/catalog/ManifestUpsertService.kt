package riven.core.service.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.entity.catalog.*
import riven.core.enums.common.icon.IconColour
import riven.core.enums.common.icon.IconType
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.models.catalog.*
import riven.core.repository.catalog.*
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.*

/**
 * Idempotent persistence for resolved manifests.
 * Upserts the catalog entry keyed on (key, manifestType), then reconciles
 * child rows using delete-then-reinsert within a single transaction boundary.
 * Skips child reconciliation entirely when content is unchanged (hash match).
 */
@Service
class ManifestUpsertService(
    private val manifestCatalogRepository: ManifestCatalogRepository,
    private val catalogEntityTypeRepository: CatalogEntityTypeRepository,
    private val catalogRelationshipRepository: CatalogRelationshipRepository,
    private val catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository,
    private val catalogFieldMappingRepository: CatalogFieldMappingRepository,
    private val catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger
) {

    // ------ Public API ------

    /**
     * Persists a resolved manifest idempotently. Creates or updates the catalog entry,
     * then deletes and reinserts all child rows. Stale manifests only update the catalog row.
     * When content hash matches, skips child reconciliation entirely to stabilize child IDs.
     */
    @Transactional
    fun upsertManifest(resolved: ResolvedManifest) {
        val contentHash = if (!resolved.stale) computeContentHash(resolved) else null
        val existing = manifestCatalogRepository.findByKeyAndManifestType(resolved.key, resolved.type)

        // Content unchanged — skip child reconciliation entirely
        if (existing != null && contentHash != null && existing.contentHash == contentHash) {
            existing.lastLoadedAt = ZonedDateTime.now()
            manifestCatalogRepository.save(existing)
            return
        }

        val catalog = persistCatalogEntry(existing, resolved, contentHash)
        val manifestId = catalog.id!!

        if (resolved.stale) {
            return
        }

        deleteChildren(manifestId)
        insertEntityTypes(manifestId, resolved.entityTypes)
        insertRelationships(manifestId, resolved.relationships)
        insertFieldMappings(manifestId, resolved.fieldMappings)
    }

    // ------ Private Helpers ------

    private fun persistCatalogEntry(
        existing: ManifestCatalogEntity?,
        resolved: ResolvedManifest,
        contentHash: String?
    ): ManifestCatalogEntity {
        val entity = if (existing != null) {
            existing.copy(
                name = resolved.name,
                description = resolved.description,
                manifestVersion = resolved.manifestVersion,
                lastLoadedAt = ZonedDateTime.now(),
                stale = resolved.stale,
                contentHash = contentHash
            )
        } else {
            ManifestCatalogEntity(
                key = resolved.key,
                name = resolved.name,
                description = resolved.description,
                manifestType = resolved.type,
                manifestVersion = resolved.manifestVersion,
                lastLoadedAt = ZonedDateTime.now(),
                stale = resolved.stale,
                contentHash = contentHash
            )
        }

        return manifestCatalogRepository.save(entity)
    }

    private fun computeContentHash(resolved: ResolvedManifest): String {
        val content = objectMapper.writeValueAsString(
            mapOf(
                "name" to resolved.name,
                "description" to resolved.description,
                "manifestVersion" to resolved.manifestVersion,
                "entityTypes" to resolved.entityTypes,
                "relationships" to resolved.relationships,
                "fieldMappings" to resolved.fieldMappings
            )
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun deleteChildren(manifestId: UUID) {
        // Delete in cascading order to avoid FK violations
        val existingRelationshipIds = catalogRelationshipRepository.findByManifestId(manifestId)
            .mapNotNull { it.id }
        if (existingRelationshipIds.isNotEmpty()) {
            catalogRelationshipTargetRuleRepository.deleteByCatalogRelationshipIdIn(existingRelationshipIds)
        }
        catalogRelationshipRepository.deleteByManifestId(manifestId)

        val existingEntityTypeIds = catalogEntityTypeRepository.findByManifestId(manifestId)
            .mapNotNull { it.id }
        if (existingEntityTypeIds.isNotEmpty()) {
            catalogSemanticMetadataRepository.deleteByCatalogEntityTypeIdIn(existingEntityTypeIds)
        }
        catalogEntityTypeRepository.deleteByManifestId(manifestId)

        catalogFieldMappingRepository.deleteByManifestId(manifestId)

        // Flush pending deletes before inserts to avoid unique constraint violations.
        // Derived delete methods use em.remove() which defers SQL execution until flush time.
        catalogEntityTypeRepository.flush()
    }

    private fun insertEntityTypes(manifestId: UUID, entityTypes: List<ResolvedEntityType>) {
        val entities = entityTypes.map { et ->
            CatalogEntityTypeEntity(
                manifestId = manifestId,
                key = et.key,
                displayNameSingular = et.displayNameSingular,
                displayNamePlural = et.displayNamePlural,
                iconType = IconType.valueOf(et.iconType),
                iconColour = IconColour.valueOf(et.iconColour),
                semanticGroup = SemanticGroup.valueOf(et.semanticGroup),
                identifierKey = et.identifierKey,
                readonly = et.readonly,
                schema = et.schema,
                columns = et.columns
            )
        }
        val savedEntityTypes = catalogEntityTypeRepository.saveAll(entities)

        // Insert semantic metadata for entity types that have semantics
        val semanticMetadata = mutableListOf<CatalogSemanticMetadataEntity>()
        savedEntityTypes.forEachIndexed { index, savedEntity ->
            val resolvedSemantics = entityTypes[index].semantics ?: return@forEachIndexed
            semanticMetadata.add(
                CatalogSemanticMetadataEntity(
                    catalogEntityTypeId = savedEntity.id!!,
                    targetType = SemanticMetadataTargetType.ENTITY_TYPE,
                    targetId = savedEntity.key,
                    definition = resolvedSemantics.definition,
                    tags = resolvedSemantics.tags
                )
            )
        }
        if (semanticMetadata.isNotEmpty()) {
            catalogSemanticMetadataRepository.saveAll(semanticMetadata)
        }
    }

    private fun insertRelationships(manifestId: UUID, relationships: List<NormalizedRelationship>) {
        for (rel in relationships) {
            val savedRel = catalogRelationshipRepository.save(
                CatalogRelationshipEntity(
                    manifestId = manifestId,
                    key = rel.key,
                    sourceEntityTypeKey = rel.sourceEntityTypeKey,
                    name = rel.name,
                    iconType = IconType.valueOf(rel.iconType),
                    iconColour = IconColour.valueOf(rel.iconColour),
                    allowPolymorphic = rel.allowPolymorphic,
                    cardinalityDefault = rel.cardinalityDefault,
                    `protected` = rel.`protected`
                )
            )

            val targetRuleEntities = rel.targetRules.map { rule ->
                CatalogRelationshipTargetRuleEntity(
                    catalogRelationshipId = savedRel.id!!,
                    targetEntityTypeKey = rule.targetEntityTypeKey,
                    semanticTypeConstraint = rule.semanticTypeConstraint?.let { SemanticGroup.valueOf(it) },
                    cardinalityOverride = rule.cardinalityOverride,
                    inverseVisible = rule.inverseVisible ?: false,
                    inverseName = rule.inverseName
                )
            }
            if (targetRuleEntities.isNotEmpty()) {
                catalogRelationshipTargetRuleRepository.saveAll(targetRuleEntities)
            }
        }
    }

    private fun insertFieldMappings(manifestId: UUID, fieldMappings: List<ResolvedFieldMapping>) {
        val entities = fieldMappings.map { fm ->
            CatalogFieldMappingEntity(
                manifestId = manifestId,
                entityTypeKey = fm.entityTypeKey,
                mappings = fm.mappings
            )
        }
        if (entities.isNotEmpty()) {
            catalogFieldMappingRepository.saveAll(entities)
        }
    }
}
