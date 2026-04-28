package riven.core.service.ingestion

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityAttributeEntity
import riven.core.entity.entity.EntityEntity
import riven.core.models.ingestion.MatchType
import riven.core.models.ingestion.ResolutionResult
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityRepository
import riven.core.service.identity.EntityTypeClassificationService
import java.util.UUID

/**
 * Batch identity resolution for the projection pipeline.
 *
 * Resolves integration entities to existing core lifecycle entities using a two-query strategy:
 * 1. sourceExternalId match — fast, O(1) per entity
 * 2. IDENTIFIER attribute value match — fallback for entities without matching external IDs
 *
 * Returns a map of integration entity ID → resolution result for each entity in the batch.
 */
@Service
class IdentityResolutionService(
    private val entityRepository: EntityRepository,
    private val entityAttributeRepository: EntityAttributeRepository,
    private val classificationService: EntityTypeClassificationService,
    private val logger: KLogger,
) {

    /**
     * Resolves a batch of integration entities against existing core entities of the target type.
     *
     * @param entities the integration entities to resolve
     * @param workspaceId the workspace scope
     * @param targetEntityTypeId the core entity type to match against
     * @return map of integration entity ID → resolution result
     */
    fun resolveBatch(
        entities: List<EntityEntity>,
        workspaceId: UUID,
        targetEntityTypeId: UUID,
    ): Map<UUID, ResolutionResult> {
        if (entities.isEmpty()) return emptyMap()

        val results = mutableMapOf<UUID, ResolutionResult>()

        // Check 1: Batch sourceExternalId match
        val externalIdMatches = resolveByExternalId(entities, workspaceId, targetEntityTypeId)
        results.putAll(externalIdMatches)

        // Check 2: Identifier key match for unmatched entities
        val unmatched = entities.filter { requireNotNull(it.id) !in results }
        if (unmatched.isNotEmpty()) {
            val identifierMatches = resolveByIdentifierKey(unmatched, workspaceId, targetEntityTypeId)
            results.putAll(identifierMatches)
        }

        // Remaining entities have no match
        val stillUnmatched = entities.filter { requireNotNull(it.id) !in results }
        for (entity in stillUnmatched) {
            results[requireNotNull(entity.id)] = ResolutionResult.NewEntity()
        }

        return results
    }

    // ------ Check 1: sourceExternalId match ------

    /**
     * Batch sourceExternalId match on the target entity type.
     * Queries entities of the target type whose sourceExternalId matches any integration entity's sourceExternalId.
     */
    private fun resolveByExternalId(
        entities: List<EntityEntity>,
        workspaceId: UUID,
        targetEntityTypeId: UUID,
    ): Map<UUID, ResolutionResult> {
        val externalIdToIntegrationEntity = entities
            .filter { it.sourceExternalId != null }
            .associateBy { it.sourceExternalId!! }

        if (externalIdToIntegrationEntity.isEmpty()) return emptyMap()

        val coreEntities = entityRepository.findByTypeIdAndWorkspaceIdAndSourceExternalIdIn(
            entityTypeId = targetEntityTypeId,
            workspaceId = workspaceId,
            sourceExternalIds = externalIdToIntegrationEntity.keys,
        )

        val results = mutableMapOf<UUID, ResolutionResult>()
        for (coreEntity in coreEntities) {
            val integrationEntity = externalIdToIntegrationEntity[coreEntity.sourceExternalId] ?: continue
            val integrationEntityId = requireNotNull(integrationEntity.id)
            results[integrationEntityId] = ResolutionResult.ExistingEntity(
                entityId = requireNotNull(coreEntity.id),
                matchType = MatchType.EXTERNAL_ID,
            )
        }

        if (results.isNotEmpty()) {
            logger.debug { "Check 1 (sourceExternalId): matched ${results.size}/${entities.size} entities" }
        }

        return results
    }

    // ------ Check 2: IDENTIFIER attribute value match ------

    /**
     * Fallback identity resolution using IDENTIFIER-classified attribute values.
     * Extracts identifier values from integration entities, then queries the target type's
     * entity attributes for matching values.
     */
    private fun resolveByIdentifierKey(
        entities: List<EntityEntity>,
        workspaceId: UUID,
        targetEntityTypeId: UUID,
    ): Map<UUID, ResolutionResult> {
        val identifierAttrIds = classificationService.getIdentifierAttributeIds(targetEntityTypeId)
        if (identifierAttrIds.isEmpty()) return emptyMap()

        val identifierValueToEntityId = extractIdentifierValues(entities, identifierAttrIds)
        if (identifierValueToEntityId.isEmpty()) return emptyMap()

        val matchingAttributes = entityAttributeRepository.findByIdentifierValuesForEntityType(
            workspaceId = workspaceId,
            entityTypeId = targetEntityTypeId,
            attributeIds = identifierAttrIds,
            textValues = identifierValueToEntityId.keys,
        )

        val results = buildResolutionResults(identifierValueToEntityId, matchingAttributes)

        if (results.isNotEmpty()) {
            logger.debug { "Check 2 (identifierKey): matched ${results.count { it.value is ResolutionResult.ExistingEntity }}/${entities.size} entities" }
        }

        return results
    }

    /**
     * Extracts identifier attribute values from integration entities, returning a map of
     * text value to the integration entity ID that owns it.
     */
    private fun extractIdentifierValues(
        entities: List<EntityEntity>,
        identifierAttrIds: Set<UUID>,
    ): Map<String, UUID> {
        val entityIds = entities.mapNotNull { it.id }
        val allAttributes = entityAttributeRepository.findByEntityIdIn(entityIds)
        val attrsByEntity = allAttributes.groupBy { it.entityId }
        val identifierValueToEntityId = mutableMapOf<String, UUID>()

        for (entity in entities) {
            val entityId = requireNotNull(entity.id)
            val entityAttrs = attrsByEntity[entityId] ?: continue

            for (attr in entityAttrs) {
                if (attr.attributeId in identifierAttrIds) {
                    val textValue = attr.value?.get("value")?.asString()
                    if (!textValue.isNullOrBlank()) {
                        identifierValueToEntityId[textValue] = entityId
                    }
                }
            }
        }

        return identifierValueToEntityId
    }

    /**
     * Groups matched attributes by identifier value and builds resolution results,
     * handling ambiguous matches (multiple core entities sharing an identifier value).
     */
    private fun buildResolutionResults(
        identifierValueToEntityId: Map<String, UUID>,
        matchingAttributes: List<EntityAttributeEntity>,
    ): Map<UUID, ResolutionResult> {
        val matchesByValue = matchingAttributes.groupBy { it.value?.get("value")?.asString() }
        val results = mutableMapOf<UUID, ResolutionResult>()

        for ((textValue, integrationEntityId) in identifierValueToEntityId) {
            val coreMatches = matchesByValue[textValue] ?: continue
            val distinctCoreEntityIds = coreMatches.map { it.entityId }.distinct()

            if (distinctCoreEntityIds.size == 1) {
                results[integrationEntityId] = ResolutionResult.ExistingEntity(
                    entityId = distinctCoreEntityIds.first(),
                    matchType = MatchType.IDENTIFIER_KEY,
                )
            } else if (distinctCoreEntityIds.size > 1) {
                logger.warn {
                    "Ambiguous identifier match for value '$textValue': " +
                        "${distinctCoreEntityIds.size} core entities found. Treating as new."
                }
                results[integrationEntityId] = ResolutionResult.NewEntity(
                    warnings = listOf("Ambiguous match: ${distinctCoreEntityIds.size} entities share identifier value '$textValue'"),
                )
            }
        }

        return results
    }
}
