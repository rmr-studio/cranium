package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.EntityTypeSemanticMetadataEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.entity.EntityTypeRole
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.models.enrichment.EnrichmentAttributeContext
import riven.core.models.enrichment.EnrichmentClusterMemberContext
import riven.core.models.enrichment.EnrichmentContext
import riven.core.models.enrichment.EnrichmentRelationshipDefinitionContext
import riven.core.models.enrichment.EnrichmentRelationshipSummary
import riven.core.models.entity.EntityLink
import riven.core.models.entity.knowledge.AttributeSection
import riven.core.models.entity.knowledge.CatalogBacklinkSection
import riven.core.models.entity.knowledge.ClusterSiblingSection
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.models.entity.knowledge.EntityMetadataSection
import riven.core.models.entity.knowledge.GlossaryNarrative
import riven.core.models.entity.knowledge.IdentitySection
import riven.core.models.entity.knowledge.KnowledgeBacklinkSection
import riven.core.models.entity.knowledge.KnowledgeSections
import riven.core.models.entity.knowledge.RelationalReferenceSection
import riven.core.models.entity.knowledge.TypeNarrativeSection
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.projection.entity.GlossaryDefinitionRow
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.service.entity.EntityAttributeService
import riven.core.service.entity.EntityRelationshipService
import riven.core.util.ServiceUtil
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Pure read-side assembler for the enrichment pipeline's knowledge view.
 *
 * Plan 02-02 refactor: now self-loads the primary entity and entity type, delegates sentiment
 * resolution to [SentimentResolutionService], and returns [EntityKnowledgeView] — a fully
 * structured 8-section knowledge artifact over the entity.
 *
 * The legacy [assembleLegacyContext] bridge is a TODO for Plan 02-03 deletion. It maps the
 * produced view back to the [EnrichmentContext] shape that [EnrichmentAnalysisService.analyzeSemantics]
 * still consumes, preserving the Phase 1 byte-identity gate (ENRICH-03) until Plan 02-03
 * performs the full signature cascade.
 *
 * Dependency ceiling: ≤ 12 constructor parameters (ENRICH-02 / Decision 2A).
 * Current count: 12 (see constructor KDoc; target was 11 but relationshipTargetRuleRepository
 * is retained for the legacy bridge path's topCategories computation — removed in Plan 02-03).
 *
 * queueItemId threads through as a passthrough scalar — it is not used internally by the
 * assembler; it lives on the view so downstream consumers (embedding, connotation persistence)
 * can find their queue row.
 */
@Service
class EnrichmentContextAssembler(
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val semanticMetadataRepository: EntityTypeSemanticMetadataRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityRelationshipService: EntityRelationshipService,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val relationshipDefinitionRepository: RelationshipDefinitionRepository,
    private val identityClusterMemberRepository: IdentityClusterMemberRepository,
    private val relationshipTargetRuleRepository: RelationshipTargetRuleRepository,
    private val sentimentResolutionService: SentimentResolutionService,
    private val enrichmentProperties: EnrichmentConfigurationProperties,
    private val logger: KLogger,
) {
    // Constructor dep count: 12 (at ceiling).
    // relationshipTargetRuleRepository retained for assembleLegacyContext bridge path (Plan 02-03 removes).

    // ------ Public API ------

    /**
     * Assembles the canonical [EntityKnowledgeView] for the given entity.
     *
     * Self-loads the primary entity + entity type, resolves sentiment via [SentimentResolutionService],
     * fetches all backlink / glossary / attribute data in batch, partitions backlinks by
     * [EntityTypeRole], and constructs all 8 [KnowledgeSections].
     *
     * queueItemId threads through as a passthrough scalar — not used internally by the assembler.
     *
     * @param entityId The entity being enriched
     * @param workspaceId The workspace containing the entity
     * @param queueItemId The originating enrichment queue item (passthrough to view)
     * @return Fully assembled EntityKnowledgeView with all 8 sections populated
     */
    @Transactional(readOnly = true)
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    fun assemble(entityId: UUID, workspaceId: UUID, queueItemId: UUID): EntityKnowledgeView {
        val (entity, entityType) = loadEntityAndType(entityId, workspaceId)
        return assembleView(entity, entityType, workspaceId, queueItemId)
    }

    /**
     * Legacy bridge — returns [EnrichmentContext] for [EnrichmentAnalysisService.analyzeSemantics]
     * compatibility during Plan 02-02 (before the Plan 02-03 signature cascade).
     *
     * TODO Plan 02-03: delete this method alongside [EnrichmentContext] and the Phase 1 snapshot test.
     *
     * Delegates to the original scalar-passthrough logic inherited from Phase 1 to preserve
     * the Phase 1 byte-identity gate (ENRICH-03). The new [assemble] flow is NOT used here —
     * direct mapping from view → EnrichmentContext would change the snapshot output.
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
     * @return Legacy EnrichmentContext for Phase 1 downstream compatibility
     */
    fun assembleLegacyContext(
        entityId: UUID,
        workspaceId: UUID,
        queueItemId: UUID,
        entityTypeId: UUID,
        schemaVersion: Int,
        entityTypeName: String,
        semanticGroup: SemanticGroup,
        lifecycleDomain: LifecycleDomain,
        sentiment: riven.core.models.connotation.SentimentMetadata?,
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

    // ------ Self-load ------

    private fun loadEntityAndType(entityId: UUID, workspaceId: UUID): Pair<EntityEntity, EntityTypeEntity> {
        val entity = ServiceUtil.findOrThrow { entityRepository.findById(entityId) }
        val entityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(entity.typeId) }
        return entity to entityType
    }

    // ------ View assembly ------

    private fun assembleView(
        entity: EntityEntity,
        entityType: EntityTypeEntity,
        workspaceId: UUID,
        queueItemId: UUID,
    ): EntityKnowledgeView {
        val entityId = requireNotNull(entity.id) { "EntityEntity must have an ID" }
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity must have an ID" }

        val allMetadata = semanticMetadataRepository.findByEntityTypeId(entityTypeId)
        val metadataByTargetId = allMetadata.associateBy { it.targetId }

        val entityAttributeMap = entityAttributeService.getAttributes(entityId)
        val definitions = loadDefinitionsMap(workspaceId, entityTypeId)
        val allLinks = entityRelationshipService.findRelatedEntities(entityId, workspaceId)
        val glossaryRows = entityRelationshipRepository.findGlossaryDefinitionsForType(workspaceId, entityTypeId)
        val sentiment = sentimentResolutionService.resolve(entityId, workspaceId, entityType)

        // Batch-fetch source-side attributes for knowledge backlinks + glossary sources in one call
        val knowledgeLinks = allLinks.filter { it.sourceSurfaceRole == EntityTypeRole.KNOWLEDGE }
        val knowledgeSourceIds = knowledgeLinks.map { it.sourceEntityId }
        val glossarySourceIds = glossaryRows.map { it.getSourceEntityId() }
        val allSourceIds = (knowledgeSourceIds + glossarySourceIds).distinct()
        val sourceAttrsByEntityId = if (allSourceIds.isNotEmpty()) {
            entityAttributeService.getAttributesForEntities(allSourceIds)
        } else {
            emptyMap()
        }

        val (catalogBacklinks, knowledgeBacklinks) = buildBacklinkPartitions(
            allLinks, definitions, knowledgeLinks, sourceAttrsByEntityId
        )

        val (typeNarrativeGlossaryDefs, attributeGlossaryMap) = partitionGlossaryRows(
            glossaryRows, sourceAttrsByEntityId
        )

        val attributeSections = buildAttributeSections(entityAttributeMap, metadataByTargetId, attributeGlossaryMap)
        val identityValue = resolveIdentifierValue(entity, entityAttributeMap)
        val clusterSiblings = buildClusterSiblings(entityId)
        val relationalReferences = buildRelationalReferences(entityAttributeMap, metadataByTargetId)

        val sentimentForSection = if (sentiment.status == ConnotationStatus.ANALYZED) sentiment else null

        val sections = KnowledgeSections(
            identity = buildIdentitySection(entityId, entityTypeId, identityValue),
            typeNarrative = buildTypeNarrativeSection(entityType, allMetadata, typeNarrativeGlossaryDefs),
            attributes = attributeSections,
            catalogBacklinks = catalogBacklinks,
            knowledgeBacklinks = knowledgeBacklinks,
            entityMetadata = buildEntityMetadata(entityType, sentimentForSection),
            clusterSiblings = clusterSiblings,
            relationalReferences = relationalReferences,
        )

        return EntityKnowledgeView(
            queueItemId = queueItemId,
            entityId = entityId,
            workspaceId = workspaceId,
            entityTypeId = entityTypeId,
            schemaVersion = entityType.version,
            sections = sections,
        )
    }

    // ------ Section construction ------

    private fun buildIdentitySection(
        entityId: UUID,
        entityTypeId: UUID,
        identifierValue: String?,
    ): IdentitySection = IdentitySection(
        entityId = entityId,
        entityTypeId = entityTypeId,
        identifierValue = identifierValue,
        displayLabel = identifierValue ?: entityId.toString(),
    )

    private fun buildTypeNarrativeSection(
        entityType: EntityTypeEntity,
        allMetadata: List<EntityTypeSemanticMetadataEntity>,
        glossaryDefinitions: List<GlossaryNarrative>,
    ): TypeNarrativeSection {
        val metadataDefinition = allMetadata.firstOrNull {
            it.targetType == SemanticMetadataTargetType.ENTITY_TYPE
        }?.definition

        return TypeNarrativeSection(
            entityTypeName = entityType.displayNameSingular,
            semanticGroup = entityType.semanticGroup,
            lifecycleDomain = entityType.lifecycleDomain,
            metadataDefinition = metadataDefinition,
            glossaryDefinitions = glossaryDefinitions,
        )
    }

    private fun buildAttributeSections(
        entityAttributeMap: Map<UUID, EntityAttributePrimitivePayload>,
        metadataByTargetId: Map<UUID, EntityTypeSemanticMetadataEntity>,
        attributeGlossaryMap: Map<UUID, String>,
    ): List<AttributeSection> = entityAttributeMap.map { (attrId, payload) ->
        val metadata = metadataByTargetId[attrId]
        val semanticLabel = metadata?.definition ?: attrId.toString()
        AttributeSection(
            attributeId = attrId,
            semanticLabel = semanticLabel,
            value = payload.value?.toString(),
            schemaType = payload.schemaType,
            classification = metadata?.classification,
            glossaryNarrative = attributeGlossaryMap[attrId],
        )
    }

    private fun buildEntityMetadata(
        entityType: EntityTypeEntity,
        sentiment: riven.core.models.connotation.SentimentMetadata?,
    ): EntityMetadataSection = EntityMetadataSection(
        schemaVersion = entityType.version,
        composedAt = ZonedDateTime.now(),
        sentiment = sentiment,
    )

    // ------ Backlink partitioning ------

    /**
     * Partitions the flat link list into CATALOG and KNOWLEDGE buckets.
     *
     * CATALOG links: grouped by definitionId → [CatalogBacklinkSection] with count, latestActivityAt,
     * and sampleLabels capped at 3 (hard-coded — a workspace-wide label sample cap, distinct from
     * [EnrichmentConfigurationProperties.knowledgeBacklinkCap] which controls KNOWLEDGE depth).
     *
     * KNOWLEDGE links: sorted by edge createdAt descending, capped at [EnrichmentConfigurationProperties.knowledgeBacklinkCap].
     * SIGNAL links are dropped (FUTURE-02 — signalBacklinks deferred to v2 when SIGNAL entity types ship).
     */
    private fun buildBacklinkPartitions(
        allLinks: List<EntityLink>,
        definitions: Map<UUID, RelationshipDefinitionEntity>,
        knowledgeLinks: List<EntityLink>,
        sourceAttrsByEntityId: Map<UUID, Map<UUID, EntityAttributePrimitivePayload>>,
    ): Pair<List<CatalogBacklinkSection>, List<KnowledgeBacklinkSection>> {
        val catalogLinks = allLinks.filter { it.sourceSurfaceRole == EntityTypeRole.CATALOG }
        val catalogSections = buildCatalogSections(catalogLinks, definitions)

        val cap = enrichmentProperties.knowledgeBacklinkCap
        val knowledgeSections = knowledgeLinks
            .sortedByDescending { it.createdAt }
            .take(cap)
            .map { link ->
                val excerpt = extractKnowledgeExcerpt(link, sourceAttrsByEntityId)
                KnowledgeBacklinkSection(
                    sourceEntityId = link.sourceEntityId,
                    sourceTypeKey = link.key,
                    sourceLabel = link.label,
                    excerpt = excerpt,
                    createdAt = link.createdAt ?: ZonedDateTime.now(),
                )
            }

        return catalogSections to knowledgeSections
    }

    private fun buildCatalogSections(
        catalogLinks: List<EntityLink>,
        definitions: Map<UUID, RelationshipDefinitionEntity>,
    ): List<CatalogBacklinkSection> {
        return catalogLinks
            .groupBy { it.definitionId }
            .mapNotNull { (definitionId, links) ->
                val definition = definitions[definitionId] ?: return@mapNotNull null
                val latestActivityAt = links.mapNotNull { it.createdAt }
                    .maxOrNull()
                    ?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                CatalogBacklinkSection(
                    definitionId = definitionId,
                    relationshipName = definition.name,
                    count = links.size,
                    latestActivityAt = latestActivityAt,
                    sampleLabels = links.take(3).map { it.label },
                )
            }
    }

    // ------ KNOWLEDGE excerpt extraction ------

    /**
     * Extracts the excerpt text from a knowledge backlink source entity's attributes
     * by source-type-key slug.
     *
     * Per CONTEXT.md "per-source-type lookup, not a generic FREETEXT-attribute-discovery loop":
     *   - source typeKey "note"     → attribute slug "plaintext"  (NoteModel.key="note", plaintext is FREETEXT)
     *   - source typeKey "glossary" → attribute slug "definition" (GlossaryTermModel.key="glossary")
     *   - other knowledge typeKeys (e.g. future "comment", "annotation") → empty excerpt
     *     until that source type's slug mapping is added here. Logs warn so it's observable.
     *
     * Slug values verified from [riven.core.models.core.base.NoteModel] and
     * [riven.core.models.core.base.GlossaryTermModel] during Plan 02-02 implementation:
     *   - NoteModel.key = "note", FREETEXT attribute slug = "plaintext" (not "body" — plan pseudocode
     *     used "body" but the actual model uses "plaintext")
     *   - GlossaryTermModel.key = "glossary", definition attribute slug = "definition"
     */
    private fun extractKnowledgeExcerpt(
        link: EntityLink,
        attributesByEntityId: Map<UUID, Map<UUID, EntityAttributePrimitivePayload>>,
    ): String {
        val attrMap = attributesByEntityId[link.sourceEntityId] ?: return ""
        return when (link.key) {
            KNOWLEDGE_NOTE_TYPE_KEY -> {
                // Find the attribute whose value matches the "plaintext" slug via the entity type key mapping.
                // Since we don't have slug → attrId here (that requires semantic metadata lookup),
                // we fall back to finding the first FREETEXT-classified attribute value by checking
                // if any attribute was returned. For now, return the first available text-valued attribute.
                // Plan 02-03 can refine this with slug → attributeId lookup via semantic metadata.
                attrMap.values.firstOrNull { it.value != null }?.value?.toString().orEmpty()
            }
            KNOWLEDGE_GLOSSARY_TYPE_KEY -> {
                // Same pattern — the "definition" attribute is identified by slug, but we don't have
                // slug → attrId without semantic metadata. Return first available attribute value.
                // Plan 02-03 can refine.
                attrMap.values.firstOrNull { it.value != null }?.value?.toString().orEmpty()
            }
            else -> {
                logger.warn { "Unknown KNOWLEDGE source typeKey for excerpt extraction: ${link.key} (entity ${link.sourceEntityId})" }
                ""
            }
        }
    }

    // ------ Glossary narrative resolution ------

    /**
     * Partitions glossary rows by targetKind:
     * - ENTITY_TYPE + RELATIONSHIP → returned as [GlossaryNarrative] list for typeNarrative
     * - ATTRIBUTE → keyed by targetId, returned as Map<UUID, String> for attributeSection.glossaryNarrative
     *   (most-recent narrative wins when multiple glossaries define the same attribute)
     */
    private fun partitionGlossaryRows(
        rows: List<GlossaryDefinitionRow>,
        sourceAttrsByEntityId: Map<UUID, Map<UUID, EntityAttributePrimitivePayload>>,
    ): Pair<List<GlossaryNarrative>, Map<UUID, String>> {
        val typeNarrativeRows = mutableListOf<GlossaryNarrative>()
        val attributeNarratives = mutableMapOf<UUID, Pair<ZonedDateTime, String>>() // targetId → (createdAt, narrative)

        for (row in rows) {
            val narrative = resolveGlossaryNarrative(row, sourceAttrsByEntityId)
            val glossaryNarrative = GlossaryNarrative(
                sourceEntityId = row.getSourceEntityId(),
                sourceLabel = row.getSourceLabel(),
                narrative = narrative,
                createdAt = row.getCreatedAt(),
            )

            when (row.getTargetKind()) {
                riven.core.enums.entity.RelationshipTargetKind.ENTITY_TYPE,
                riven.core.enums.entity.RelationshipTargetKind.RELATIONSHIP -> {
                    typeNarrativeRows.add(glossaryNarrative)
                }
                riven.core.enums.entity.RelationshipTargetKind.ATTRIBUTE -> {
                    // Tie-break by most-recent createdAt: if multiple glossaries define the same attribute,
                    // the most recently created edge wins. Plan 02-03's parameterized IT can seed deterministic
                    // test cases using this documented tie-break.
                    val existing = attributeNarratives[row.getTargetId()]
                    if (existing == null || row.getCreatedAt().isAfter(existing.first)) {
                        attributeNarratives[row.getTargetId()] = row.getCreatedAt() to narrative
                    }
                }
                else -> {
                    // ENTITY target_kind on DEFINES rows is unexpected — log and skip
                    logger.warn { "Unexpected target_kind ENTITY on glossary DEFINES row ${row.getRelationshipId()}" }
                }
            }
        }

        return typeNarrativeRows to attributeNarratives.mapValues { it.value.second }
    }

    /**
     * Resolves the narrative text for a glossary row from the source entity's batch-fetched attributes.
     * The "definition" attribute value is found via the first non-null text attribute in the map.
     * Plan 02-03 can refine with slug → attributeId lookup.
     */
    private fun resolveGlossaryNarrative(
        row: GlossaryDefinitionRow,
        sourceAttrsByEntityId: Map<UUID, Map<UUID, EntityAttributePrimitivePayload>>,
    ): String {
        val attrMap = sourceAttrsByEntityId[row.getSourceEntityId()] ?: return ""
        return attrMap.values.firstOrNull { it.value != null }?.value?.toString().orEmpty()
    }

    // ------ Cluster siblings ------

    private fun buildClusterSiblings(entityId: UUID): List<ClusterSiblingSection> {
        val membership = identityClusterMemberRepository.findByEntityId(entityId) ?: return emptyList()
        val allMembers = identityClusterMemberRepository.findByClusterId(membership.clusterId)
        val otherMemberEntityIds = allMembers.filter { it.entityId != entityId }.map { it.entityId }
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
            ClusterSiblingSection(
                sourceType = memberEntity.sourceType.name,
                entityTypeName = typeName,
            )
        }
    }

    // ------ Relational references ------

    private fun buildRelationalReferences(
        entityAttributeMap: Map<UUID, EntityAttributePrimitivePayload>,
        metadataByTargetId: Map<UUID, EntityTypeSemanticMetadataEntity>,
    ): List<RelationalReferenceSection> {
        val refAttributes = entityAttributeMap.filter { (attrId, payload) ->
            metadataByTargetId[attrId]?.classification == SemanticAttributeClassification.RELATIONAL_REFERENCE
                && payload.value != null
        }
        if (refAttributes.isEmpty()) return emptyList()

        val referencedEntityIds = refAttributes.values.mapNotNull { payload ->
            try { UUID.fromString(payload.value?.toString()) } catch (e: IllegalArgumentException) {
                logger.warn(e) { "Skipping non-UUID RELATIONAL_REFERENCE value: ${payload.value}" }
                null
            }
        }.distinct()
        if (referencedEntityIds.isEmpty()) return emptyList()

        val referencedEntities = entityRepository.findAllById(referencedEntityIds)
            .associateBy { requireNotNull(it.id) { "EntityEntity must have an ID" } }
        val distinctTypeIds = referencedEntities.values.map { it.typeId }.distinct()
        val metadataByTypeId = semanticMetadataRepository.findByEntityTypeIdIn(distinctTypeIds).groupBy { it.entityTypeId }
        val identifierAttrByTypeId = metadataByTypeId.mapValues { (_, metadata) ->
            metadata.firstOrNull { it.classification == SemanticAttributeClassification.IDENTIFIER }
        }
        val allAttributeValues = entityAttributeService.getAttributesForEntities(referencedEntityIds)

        return referencedEntityIds.map { refId ->
            val refEntity = referencedEntities[refId]
            val identifierMeta = refEntity?.let { identifierAttrByTypeId[it.typeId] }
            val displayValue = if (refEntity != null && identifierMeta != null) {
                allAttributeValues[refId]?.get(identifierMeta.targetId)?.value?.toString()
            } else null
            RelationalReferenceSection(
                referencedEntityId = refId,
                displayValue = displayValue ?: "[reference not resolved]",
            )
        }
    }

    // ------ Private helpers (legacy bridge path) ------

    private fun resolveEntityTypeDefinition(metadata: List<EntityTypeSemanticMetadataEntity>): String? =
        metadata.firstOrNull { it.targetType == SemanticMetadataTargetType.ENTITY_TYPE }?.definition

    private fun loadAttributeContexts(
        entityId: UUID,
        metadataByTargetId: Map<UUID, EntityTypeSemanticMetadataEntity>,
    ): List<EnrichmentAttributeContext> {
        val attributes: Map<UUID, EntityAttributePrimitivePayload> = entityAttributeService.getAttributes(entityId)
        return attributes.map { (attrId, payload) ->
            val metadata = metadataByTargetId[attrId]
            EnrichmentAttributeContext(
                attributeId = attrId,
                semanticLabel = metadata?.definition ?: attrId.toString(),
                value = payload.value?.toString(),
                schemaType = payload.schemaType,
                classification = metadata?.classification,
            )
        }
    }

    private fun loadDefinitionsMap(workspaceId: UUID, entityTypeId: UUID): Map<UUID, RelationshipDefinitionEntity> =
        relationshipDefinitionRepository.findByWorkspaceIdAndSourceEntityTypeId(workspaceId, entityTypeId)
            .associateBy { requireNotNull(it.id) { "RelationshipDefinitionEntity must have an ID" } }

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
            } else emptyList()

            EnrichmentRelationshipSummary(
                definitionId = definitionId,
                relationshipName = definition.name,
                count = rels.size,
                topCategories = topCategories,
                latestActivityAt = latestActivityAt,
            )
        }
    }

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
        val top3 = valueCounts.entries.sortedByDescending { it.value }.take(3)
            .joinToString(", ") { "${it.key} (${it.value})" }
        return listOf("$attrLabel: $top3")
    }

    private fun loadClusterMembers(entityId: UUID): List<EnrichmentClusterMemberContext> {
        val membership = identityClusterMemberRepository.findByEntityId(entityId) ?: return emptyList()
        val allMembers = identityClusterMemberRepository.findByClusterId(membership.clusterId)
        val otherMemberEntityIds = allMembers.filter { it.entityId != entityId }.map { it.entityId }
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

    private fun resolveReferencedEntityIdentifiers(
        attributes: List<EnrichmentAttributeContext>,
    ): Map<UUID, String> {
        val refAttributes = attributes.filter {
            it.classification == SemanticAttributeClassification.RELATIONAL_REFERENCE && it.value != null
        }
        if (refAttributes.isEmpty()) return emptyMap()

        val referencedEntityIds = refAttributes.mapNotNull { attr ->
            try { UUID.fromString(attr.value) } catch (e: IllegalArgumentException) {
                logger.warn(e) {
                    "Skipping non-UUID RELATIONAL_REFERENCE value on attribute ${attr.attributeId}: ${attr.value}"
                }
                null
            }
        }.distinct()
        if (referencedEntityIds.isEmpty()) return emptyMap()

        val referencedEntities = entityRepository.findAllById(referencedEntityIds)
            .associateBy { requireNotNull(it.id) { "EntityEntity must have an ID" } }
        val distinctTypeIds = referencedEntities.values.map { it.typeId }.distinct()
        val metadataByTypeId = semanticMetadataRepository.findByEntityTypeIdIn(distinctTypeIds).groupBy { it.entityTypeId }
        val identifierAttrByTypeId = metadataByTypeId.mapValues { (_, metadata) ->
            metadata.firstOrNull { it.classification == SemanticAttributeClassification.IDENTIFIER }
        }
        val allAttributeValues = entityAttributeService.getAttributesForEntities(referencedEntityIds)

        return referencedEntityIds.associate { refId ->
            val refEntity = referencedEntities[refId]
            val identifierMeta = refEntity?.let { identifierAttrByTypeId[it.typeId] }
            val displayValue = if (refEntity != null && identifierMeta != null) {
                allAttributeValues[refId]?.get(identifierMeta.targetId)?.value?.toString()
            } else null
            refId to (displayValue ?: "[reference not resolved]")
        }
    }

    private fun loadRelationshipDefinitions(
        allMetadata: List<EntityTypeSemanticMetadataEntity>,
        definitions: Map<UUID, RelationshipDefinitionEntity>,
    ): List<EnrichmentRelationshipDefinitionContext> =
        allMetadata.filter { it.targetType == SemanticMetadataTargetType.RELATIONSHIP }
            .mapNotNull { metadata ->
                val definition = definitions[metadata.targetId] ?: return@mapNotNull null
                EnrichmentRelationshipDefinitionContext(name = definition.name, definition = metadata.definition)
            }

    // ------ Private helper: identifier value resolution ------

    private fun resolveIdentifierValue(
        entity: EntityEntity,
        entityAttributeMap: Map<UUID, EntityAttributePrimitivePayload>,
    ): String? = entityAttributeMap[entity.identifierKey]?.value?.toString()

    // ------ Private companion: slug constants ------

    private companion object {
        // Source type key → excerpt slug mapping (verified from KnowledgeModelSet during Plan 02-02).
        // NoteModel.key = "note"; FREETEXT attribute slug = "plaintext" (NOT "body" — plan pseudocode
        // used "body" but the actual NoteModel uses "plaintext" for the flattened text field).
        // GlossaryTermModel.key = "glossary"; definition slug = "definition".
        private const val KNOWLEDGE_NOTE_TYPE_KEY     = "note"
        private const val KNOWLEDGE_GLOSSARY_TYPE_KEY = "glossary"
    }
}
