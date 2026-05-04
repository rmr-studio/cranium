package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityTypeSemanticMetadataEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.models.connotation.SentimentMetadata
import riven.core.models.enrichment.EnrichmentAttributeContext
import riven.core.models.enrichment.EnrichmentClusterMemberContext
import riven.core.models.enrichment.EnrichmentContext
import riven.core.models.enrichment.EnrichmentRelationshipDefinitionContext
import riven.core.models.enrichment.EnrichmentRelationshipSummary
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.service.entity.EntityAttributeService
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Pure read-side assembler for the enrichment pipeline's semantic context.
 *
 * Owns the eight read-side helpers previously co-located in [EnrichmentService] and
 * exposes one public [assemble] method consumed by [EnrichmentService.analyzeSemantics].
 * The caller is responsible for loading the primary entity and entity type, resolving
 * sentiment, and driving persistence — this service is stateless and side-effect-free.
 *
 * Extracted from [EnrichmentService] as part of Plan 01-02 decomposition.
 *
 * Dependency ceiling: ≤ 12 constructor parameters (ENRICH-02). Current count: 9.
 */
@Service
class EnrichmentContextAssembler(
    private val semanticMetadataRepository: EntityTypeSemanticMetadataRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val relationshipDefinitionRepository: RelationshipDefinitionRepository,
    private val identityClusterMemberRepository: IdentityClusterMemberRepository,
    private val relationshipTargetRuleRepository: RelationshipTargetRuleRepository,
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val logger: KLogger,
) {

    // ------ Public Assembly Method ------

    /**
     * Assembles the complete [EnrichmentContext] from repository data.
     *
     * The caller has already loaded the primary entity and entity type, computed [sentiment],
     * and extracted the scalar fields passed here. The assembler does not re-fetch the entity
     * or entity type, keeping its dependency count below the 12-param ceiling.
     *
     * Orchestrates the private read helpers in the same order as the original
     * [EnrichmentService.analyzeSemantics] implementation to preserve byte-identical output.
     *
     * @param entityId The entity being enriched
     * @param workspaceId The workspace containing the entity
     * @param queueItemId The originating enrichment queue item
     * @param entityTypeId The entity's type ID
     * @param schemaVersion The entity type's current schema version
     * @param entityTypeName The entity type's display name (singular)
     * @param semanticGroup The entity type's semantic group classification
     * @param lifecycleDomain The entity type's lifecycle domain
     * @param sentiment Pre-resolved sentiment metadata (null = NOT_APPLICABLE)
     * @return Complete context snapshot for downstream activities
     */
    fun assemble(
        entityId: UUID,
        workspaceId: UUID,
        queueItemId: UUID,
        entityTypeId: UUID,
        schemaVersion: Int,
        entityTypeName: String,
        semanticGroup: SemanticGroup,
        lifecycleDomain: LifecycleDomain,
        sentiment: SentimentMetadata?,
    ): EnrichmentContext {
        val allMetadata = semanticMetadataRepository.findByEntityTypeId(entityTypeId)
        val metadataByTargetId = allMetadata.associateBy { it.targetId }

        val entityTypeDefinition = resolveEntityTypeDefinition(allMetadata)
        val attributes = loadAttributeContexts(entityId, metadataByTargetId)
        val definitions = loadDefinitionsMap(workspaceId, entityTypeId)
        val relationshipSummaries = loadRelationshipSummaries(entityId, workspaceId, definitions)
        val clusterMembers = loadClusterMembers(entityId)
        val referencedEntityIdentifiers = resolveReferencedEntityIdentifiers(attributes)
        val relationshipDefinitions = loadRelationshipDefinitions(allMetadata, definitions)

        return EnrichmentContext(
            queueItemId = queueItemId,
            entityId = entityId,
            workspaceId = workspaceId,
            entityTypeId = entityTypeId,
            schemaVersion = schemaVersion,
            entityTypeName = entityTypeName,
            entityTypeDefinition = entityTypeDefinition,
            semanticGroup = semanticGroup,
            lifecycleDomain = lifecycleDomain,
            attributes = attributes,
            relationshipSummaries = relationshipSummaries,
            clusterMembers = clusterMembers,
            referencedEntityIdentifiers = referencedEntityIdentifiers,
            relationshipDefinitions = relationshipDefinitions,
            sentiment = sentiment,
        )
    }

    // ------ Private Helpers ------

    /**
     * Finds the entity-level semantic definition from metadata list.
     *
     * Returns the `definition` field from the row where targetType == ENTITY_TYPE,
     * or null if no such metadata exists.
     */
    private fun resolveEntityTypeDefinition(metadata: List<EntityTypeSemanticMetadataEntity>): String? =
        metadata.firstOrNull { it.targetType == SemanticMetadataTargetType.ENTITY_TYPE }?.definition

    /**
     * Maps entity attributes to [EnrichmentAttributeContext], resolving semantic labels
     * and classification from metadata.
     *
     * Looks up each attribute's metadata by targetId (matching the attribute UUID key).
     * Falls back to the attribute UUID string if no metadata is found.
     * Maps classification from semantic metadata when present.
     */
    private fun loadAttributeContexts(
        entityId: UUID,
        metadataByTargetId: Map<UUID, EntityTypeSemanticMetadataEntity>,
    ): List<EnrichmentAttributeContext> {
        val attributes: Map<UUID, EntityAttributePrimitivePayload> = entityAttributeService.getAttributes(entityId)

        return attributes.map { (attrId, payload) ->
            val metadata = metadataByTargetId[attrId]
            val semanticLabel = metadata?.definition ?: attrId.toString()
            val valueString = payload.value?.toString()

            EnrichmentAttributeContext(
                attributeId = attrId,
                semanticLabel = semanticLabel,
                value = valueString,
                schemaType = payload.schemaType,
                classification = metadata?.classification,
            )
        }
    }

    /**
     * Loads relationship definitions as a map keyed by definition ID.
     *
     * Used by both [loadRelationshipSummaries] and [loadRelationshipDefinitions] so
     * the repository is only queried once per assemble call.
     */
    private fun loadDefinitionsMap(workspaceId: UUID, entityTypeId: UUID): Map<UUID, RelationshipDefinitionEntity> =
        relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)
            .associateBy { requireNotNull(it.id) { "RelationshipDefinitionEntity must have an ID" } }

    /**
     * Groups entity relationships by definition and maps to [EnrichmentRelationshipSummary].
     *
     * Loads all relationships for the entity in one batch query, groups by definitionId,
     * then joins with relationship definitions to get human-readable names.
     * Only includes relationships with known definition names.
     *
     * Phase 3 additions: populates [EnrichmentRelationshipSummary.latestActivityAt] with the
     * ISO-8601 timestamp of the most recently created relationship per definition, and
     * [EnrichmentRelationshipSummary.topCategories] with categorical breakdowns from the
     * target entity type's CATEGORICAL attributes.
     */
    private fun loadRelationshipSummaries(
        entityId: UUID,
        workspaceId: UUID,
        definitions: Map<UUID, RelationshipDefinitionEntity>,
    ): List<EnrichmentRelationshipSummary> {
        val relationships = entityRelationshipRepository.findAllRelationshipsForEntity(entityId, workspaceId)
        if (relationships.isEmpty()) return emptyList()

        val byDefinitionId = relationships.groupBy { it.definitionId }

        val definitionIds = byDefinitionId.keys.toList()
        val targetRulesByDefinitionId = relationshipTargetRuleRepository
            .findByRelationshipDefinitionIdIn(definitionIds)
            .groupBy { it.relationshipDefinitionId }

        return byDefinitionId.mapNotNull { (definitionId, rels) ->
            val definition = definitions[definitionId] ?: return@mapNotNull null

            val latestActivityAt = rels.mapNotNull { it.createdAt }
                .maxOrNull()
                ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            val relatedEntityIds = rels.map { it.targetId }.distinct()
            val targetTypeId = targetRulesByDefinitionId[definitionId]?.firstOrNull()?.targetEntityTypeId
            val topCategories = if (targetTypeId != null) {
                loadTopCategoriesForRelationship(targetTypeId, relatedEntityIds)
            } else {
                emptyList()
            }

            EnrichmentRelationshipSummary(
                definitionId = definitionId,
                relationshipName = definition.name,
                count = rels.size,
                topCategories = topCategories,
                latestActivityAt = latestActivityAt,
            )
        }
    }

    /**
     * Loads top categorical breakdowns for the related entities of a single relationship type.
     *
     * Finds the first CATEGORICAL attribute on the target entity type, loads its values
     * across all related entity IDs, groups by value, and formats the top 3 as
     * "AttrLabel: Value1 (count), Value2 (count), Value3 (count)".
     *
     * Returns emptyList() if no CATEGORICAL attributes exist on the target type.
     */
    private fun loadTopCategoriesForRelationship(
        targetTypeId: UUID,
        relatedEntityIds: List<UUID>,
    ): List<String> {
        if (relatedEntityIds.isEmpty()) return emptyList()

        val targetMetadata = semanticMetadataRepository.findByEntityTypeId(targetTypeId)
        val categoricalAttr = targetMetadata.firstOrNull {
            it.classification == SemanticAttributeClassification.CATEGORICAL
        } ?: return emptyList()

        val attrLabel = categoricalAttr.definition ?: categoricalAttr.targetId.toString()
        val allAttrsByEntity = entityAttributeService.getAttributesForEntities(relatedEntityIds)

        val valueCounts = relatedEntityIds
            .mapNotNull { entityId -> allAttrsByEntity[entityId]?.get(categoricalAttr.targetId)?.value?.toString() }
            .groupingBy { it }
            .eachCount()

        if (valueCounts.isEmpty()) return emptyList()

        val top3 = valueCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key} (${it.value})" }

        return listOf("$attrLabel: $top3")
    }

    /**
     * Loads cluster member summaries for the entity, excluding the entity itself.
     *
     * Returns an empty list if the entity is not in any identity cluster, or if
     * it is the only member of its cluster.
     *
     * @param entityId The entity being enriched
     */
    private fun loadClusterMembers(entityId: UUID): List<EnrichmentClusterMemberContext> {
        val membership = identityClusterMemberRepository.findByEntityId(entityId) ?: return emptyList()

        val allMembers = identityClusterMemberRepository.findByClusterId(membership.clusterId)
        val otherMemberEntityIds = allMembers
            .filter { it.entityId != entityId }
            .map { it.entityId }

        if (otherMemberEntityIds.isEmpty()) return emptyList()

        val memberEntities = entityRepository.findAllById(otherMemberEntityIds)
            .associateBy { requireNotNull(it.id) { "EntityEntity must have an ID" } }

        val distinctTypeIds = memberEntities.values.map { it.typeId }.distinct()
        val typeNamesById = entityTypeRepository.findAllById(distinctTypeIds)
            .associateBy { requireNotNull(it.id) { "EntityTypeEntity must have an ID" } }
            .mapValues { it.value.displayNameSingular }

        return otherMemberEntityIds.mapNotNull { memberId ->
            val memberEntity = memberEntities[memberId] ?: return@mapNotNull null
            val typeName = typeNamesById[memberEntity.typeId] ?: return@mapNotNull null
            EnrichmentClusterMemberContext(
                sourceType = memberEntity.sourceType,
                entityTypeName = typeName,
            )
        }
    }

    /**
     * Resolves RELATIONAL_REFERENCE attribute values to their referenced entity's
     * IDENTIFIER attribute display string.
     *
     * For each attribute with classification == RELATIONAL_REFERENCE and a non-null UUID value:
     * - Loads the referenced entity
     * - Finds the IDENTIFIER-classified attribute on its entity type
     * - Returns the attribute value as the display string
     *
     * Falls back to "[reference not resolved]" when:
     * - The referenced entity ID cannot be parsed as a UUID
     * - The referenced entity's type has no IDENTIFIER attribute
     * - The IDENTIFIER attribute has a null value
     *
     * @param attributes The resolved attribute contexts for the entity being enriched
     * @return Map from referenced entity UUID to display string
     */
    private fun resolveReferencedEntityIdentifiers(
        attributes: List<EnrichmentAttributeContext>,
    ): Map<UUID, String> {
        val refAttributes = attributes.filter {
            it.classification == SemanticAttributeClassification.RELATIONAL_REFERENCE && it.value != null
        }
        if (refAttributes.isEmpty()) return emptyMap()

        val referencedEntityIds = refAttributes.mapNotNull { attr ->
            try {
                UUID.fromString(attr.value)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.distinct()

        if (referencedEntityIds.isEmpty()) return emptyMap()

        val referencedEntities = entityRepository.findAllById(referencedEntityIds)
            .associateBy { requireNotNull(it.id) { "EntityEntity must have an ID" } }

        val distinctTypeIds = referencedEntities.values.map { it.typeId }.distinct()
        val metadataByTypeId = semanticMetadataRepository.findByEntityTypeIdIn(distinctTypeIds)
            .groupBy { it.entityTypeId }

        val identifierAttrByTypeId = metadataByTypeId.mapValues { (_, metadata) ->
            metadata.firstOrNull { it.classification == SemanticAttributeClassification.IDENTIFIER }
        }

        val allAttributeValues = entityAttributeService.getAttributesForEntities(referencedEntityIds)

        return referencedEntityIds.associate { refId ->
            val refEntity = referencedEntities[refId]
            val identifierMeta = refEntity?.let { identifierAttrByTypeId[it.typeId] }
            val displayValue = if (refEntity != null && identifierMeta != null) {
                allAttributeValues[refId]?.get(identifierMeta.targetId)?.value?.toString()
            } else {
                null
            }
            refId to (displayValue ?: "[reference not resolved]")
        }
    }

    /**
     * Loads relationship semantic definitions for all RELATIONSHIP metadata entries.
     *
     * Filters allMetadata for rows where targetType == RELATIONSHIP, then joins with
     * the definitions map to get the relationship name.
     *
     * @param allMetadata All semantic metadata for the entity type
     * @param definitions Map of definition ID to RelationshipDefinitionEntity (already loaded)
     */
    private fun loadRelationshipDefinitions(
        allMetadata: List<EntityTypeSemanticMetadataEntity>,
        definitions: Map<UUID, RelationshipDefinitionEntity>,
    ): List<EnrichmentRelationshipDefinitionContext> =
        allMetadata
            .filter { it.targetType == SemanticMetadataTargetType.RELATIONSHIP }
            .mapNotNull { metadata ->
                val definition = definitions[metadata.targetId] ?: return@mapNotNull null
                EnrichmentRelationshipDefinitionContext(
                    name = definition.name,
                    definition = metadata.definition,
                )
            }
}
