package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.ObjectMapper
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.entity.enrichment.EntityEmbeddingEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.entity.EntityTypeSemanticMetadataEntity
import riven.core.entity.entity.RelationshipDefinitionEntity
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.workflow.ExecutionJobType
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.enums.entity.semantics.SemanticMetadataTargetType
import riven.core.enums.integration.SourceType
import riven.core.exceptions.NotFoundException
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.connotation.AttributeClassificationSnapshot
import riven.core.models.connotation.ClusterMemberSnapshot
import riven.core.models.connotation.EntityMetadata
import riven.core.models.connotation.EntityMetadataSnapshot
import riven.core.models.connotation.RelationalMetadata
import riven.core.models.connotation.RelationalReferenceResolution
import riven.core.models.connotation.RelationshipSemanticDefinitionSnapshot
import riven.core.models.connotation.RelationshipSummarySnapshot
import riven.core.models.connotation.SentimentMetadata
import riven.core.models.connotation.StructuralMetadata
import riven.core.models.enrichment.EnrichmentAttributeContext
import riven.core.models.enrichment.EnrichmentClusterMemberContext
import riven.core.models.enrichment.EnrichmentContext
import riven.core.models.enrichment.EnrichmentRelationshipDefinitionContext
import riven.core.models.enrichment.EnrichmentRelationshipSummary
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.repository.enrichment.EntityEmbeddingRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.entity.EntityTypeSemanticMetadataRepository
import riven.core.repository.entity.RelationshipDefinitionRepository
import riven.core.repository.entity.RelationshipTargetRuleRepository
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.connotation.ConnotationAnalysisService
import riven.core.service.enrichment.provider.EmbeddingProvider
import riven.core.service.entity.EntityAttributeService
import riven.core.service.workflow.enrichment.EnrichmentWorkflow
import riven.core.util.ServiceUtil
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Orchestration service for the entity embedding enrichment pipeline.
 *
 * Manages the full queue lifecycle from embeddability gating through
 * semantic-snapshot assembly, Temporal dispatch, and embedding storage.
 *
 * Core responsibilities:
 * - [enqueueAndProcess] — embeddability gate + queue item creation + Temporal dispatch.
 * - [enqueueByEntityType] — bulk re-enrichment for every entity of a type (manifest reconciliation hook).
 * - [analyzeSemantics] — queue item claiming + assembly of the polymorphic semantic snapshot
 *   (SENTIMENT placeholder + RELATIONAL + STRUCTURAL metadata categories) and persistence to
 *   `entity_connotation`. Returns a transient [EnrichmentContext] for downstream activities; the
 *   persisted snapshot is the source of truth for non-pipeline consumers.
 * - [storeEmbedding] — embedding upsert + queue item completion.
 *
 * **Concurrency posture:** snapshot persistence uses an atomic
 * `INSERT ... ON CONFLICT (entity_id) DO UPDATE` keyed by `entity_id`, so concurrent writers
 * always converge to a single row and race only for last-write-wins on the payload. Each writer's
 * own view is internally consistent at fetch time; the surviving row reflects whichever transaction
 * commits last. Existing queue dedup (in [enqueueAndProcess]) makes overlap rare in practice but
 * is no longer load-bearing for correctness.
 */
@Service
class EnrichmentService(
    private val executionQueueRepository: ExecutionQueueRepository,
    private val entityEmbeddingRepository: EntityEmbeddingRepository,
    private val entityConnotationRepository: EntityConnotationRepository,
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val semanticMetadataRepository: EntityTypeSemanticMetadataRepository,
    private val entityAttributeService: EntityAttributeService,
    private val entityRelationshipRepository: EntityRelationshipRepository,
    private val relationshipDefinitionRepository: RelationshipDefinitionRepository,
    private val identityClusterMemberRepository: IdentityClusterMemberRepository,
    private val relationshipTargetRuleRepository: RelationshipTargetRuleRepository,
    private val embeddingProvider: EmbeddingProvider,
    private val enrichmentProperties: EnrichmentConfigurationProperties,
    private val workflowClient: WorkflowClient,
    private val objectMapper: ObjectMapper,
    private val workspaceRepository: WorkspaceRepository,
    private val manifestCatalogService: ManifestCatalogService,
    private val connotationAnalysisService: ConnotationAnalysisService,
    private val logger: KLogger,
) {

    // ------ Public Entry Point ------

    /**
     * Enqueues an entity for enrichment and dispatches a Temporal workflow.
     *
     * INTEGRATION entities are silently skipped — they are derived from external systems
     * and are not embeddable by design. All other source types proceed through the pipeline.
     *
     * @param entityId The entity to enrich
     * @param workspaceId The workspace the entity belongs to
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun enqueueAndProcess(entityId: UUID, workspaceId: UUID) {
        val entity = ServiceUtil.findOrThrow { entityRepository.findByIdAndWorkspaceId(entityId, workspaceId) }

        if (!isEmbeddable(entity)) {
            logger.debug { "Skipping enrichment for INTEGRATION entity $entityId" }
            return
        }

        val queueItem = executionQueueRepository.save(
            ExecutionQueueEntity(
                workspaceId = workspaceId,
                jobType = ExecutionJobType.ENRICHMENT,
                entityId = entityId,
                status = ExecutionQueueStatus.PENDING,
            )
        )

        val queueItemId = requireNotNull(queueItem.id) { "Persisted ExecutionQueueEntity must have an ID" }

        startEnrichmentWorkflowAfterCommit(queueItemId)

        logger.info { "Enqueued entity $entityId for enrichment, queue item: $queueItemId" }
    }

    /**
     * Bulk-enqueue ENRICHMENT items for every non-INTEGRATION, non-deleted entity of [entityTypeId]
     * in [workspaceId]. Hooked from [riven.core.service.catalog.SchemaReconciliationService] when
     * a manifest schema change invalidates the STRUCTURAL metadata snapshots stored in
     * `entity_connotation`.
     *
     * Backed by a single `INSERT ... SELECT` in [ExecutionQueueRepository.enqueueEnrichmentByEntityType]
     * to avoid N+1 at high entity-type cardinality. The partial unique index on `execution_queue`
     * deduplicates against in-flight PENDING rows.
     *
     * Workflow dispatch is intentionally NOT triggered here — these queue items are picked up by
     * the existing enrichment dispatcher pattern.
     *
     * @return Count of rows actually inserted (excludes skipped duplicates).
     */
    @PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")
    @Transactional
    fun enqueueByEntityType(entityTypeId: UUID, workspaceId: UUID): Int {
        val inserted = executionQueueRepository.enqueueEnrichmentByEntityType(entityTypeId, workspaceId)
        logger.info { "Bulk-enqueued $inserted ENRICHMENT items for entity type $entityTypeId in workspace $workspaceId" }
        return inserted
    }

    /**
     * Registers a post-commit callback to dispatch the Temporal enrichment workflow.
     *
     * Defers workflow start until after the surrounding DB transaction commits, so the
     * queue row is guaranteed to exist before the workflow activity queries it. Prevents
     * orphaned workflows when the transaction rolls back after Temporal dispatch. When no
     * transaction is active (e.g. unit tests without a real tx), dispatches immediately
     * as a fallback — matching the pattern in
     * [riven.core.service.entity.EntityTypeSemanticMetadataService].
     */
    private fun startEnrichmentWorkflowAfterCommit(queueItemId: UUID) {
        val dispatch: () -> Unit = {
            val stub = workflowClient.newWorkflowStub(
                EnrichmentWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
                    .setWorkflowId(EnrichmentWorkflow.workflowId(queueItemId))
                    .build()
            )
            WorkflowClient.start { stub.embed(queueItemId) }
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    dispatch()
                }
            })
        } else {
            dispatch()
        }
    }

    // ------ Activity-Called Methods ------

    /**
     * Claims a queue item, computes the polymorphic semantic snapshot (SENTIMENT placeholder +
     * RELATIONAL + STRUCTURAL metadata categories), persists it to `entity_connotation`, and
     * returns a transient [EnrichmentContext] for downstream activities.
     *
     * Marks the queue item as CLAIMED (idempotent on retry — accepts CLAIMED status too).
     * Loads entity, entity type, semantic metadata, attributes, and relationships in batch
     * queries to avoid N+1 patterns.
     *
     * The persisted snapshot is "as of last enrichment" — a point-in-time view, not a live one.
     * Consumers needing live state must query the underlying tables. Last-write-wins on concurrent
     * writes; see class KDoc for concurrency posture.
     *
     * @param queueItemId The enrichment queue row to process
     * @return Complete context snapshot for downstream activities
     * @throws NotFoundException if the queue item does not exist
     */
    @Transactional
    fun analyzeSemantics(queueItemId: UUID): EnrichmentContext {
        val queueItem = ServiceUtil.findOrThrow { executionQueueRepository.findById(queueItemId) }

        val claimedItem = claimQueueItem(queueItem)
        val entityId = requireNotNull(claimedItem.entityId) { "ENRICHMENT queue item must have an entityId" }

        val entity = ServiceUtil.findOrThrow { entityRepository.findById(entityId) }
        val entityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(entity.typeId) }

        val allMetadata = semanticMetadataRepository.findByEntityTypeId(entity.typeId)
        val metadataByTargetId = allMetadata.associateBy { it.targetId }

        val entityTypeDefinition = resolveEntityTypeDefinition(allMetadata)
        val attributes = loadAttributeContexts(entityId, metadataByTargetId)
        val definitions = loadDefinitionsMap(entity.workspaceId, entity.typeId)
        val relationshipSummaries = loadRelationshipSummaries(entityId, entity.workspaceId, definitions)
        val clusterMembers = loadClusterMembers(entityId)
        val referencedEntityIdentifiers = resolveReferencedEntityIdentifiers(attributes)
        val relationshipDefinitions = loadRelationshipDefinitions(allMetadata, definitions)

        val sentimentMetadata: SentimentMetadata = resolveSentimentMetadata(entityId, entity.workspaceId, entityType)

        val context = EnrichmentContext(
            queueItemId = queueItemId,
            entityId = entityId,
            workspaceId = entity.workspaceId,
            entityTypeId = entity.typeId,
            schemaVersion = entityType.version,
            entityTypeName = entityType.displayNameSingular,
            entityTypeDefinition = entityTypeDefinition,
            semanticGroup = entityType.semanticGroup,
            lifecycleDomain = entityType.lifecycleDomain,
            attributes = attributes,
            relationshipSummaries = relationshipSummaries,
            clusterMembers = clusterMembers,
            referencedEntityIdentifiers = referencedEntityIdentifiers,
            relationshipDefinitions = relationshipDefinitions,
            sentiment = if (sentimentMetadata.status == ConnotationStatus.ANALYZED) sentimentMetadata else null,
        )

        persistConnotationSnapshot(entityId, entity.workspaceId, entityType, context, sentimentMetadata)

        return context
    }

    /**
     * Upserts the embedding record for an entity and marks the queue item as COMPLETED.
     *
     * Deletes any existing embedding first (delete + insert upsert pattern), then saves
     * the new embedding with all required metadata fields.
     *
     * @param queueItemId The queue item to mark completed
     * @param context The enrichment context snapshot (provides entity/type IDs, schema version)
     * @param embedding The generated embedding vector
     * @param truncated Whether the enriched text was truncated before embedding generation
     */
    @Transactional
    fun storeEmbedding(queueItemId: UUID, context: EnrichmentContext, embedding: FloatArray, truncated: Boolean) {
        require(embedding.size == enrichmentProperties.vectorDimensions) {
            "Embedding size ${embedding.size} does not match configured vector dimensions ${enrichmentProperties.vectorDimensions}"
        }

        entityEmbeddingRepository.deleteByEntityId(context.entityId)

        entityEmbeddingRepository.save(
            EntityEmbeddingEntity(
                workspaceId = context.workspaceId,
                entityId = context.entityId,
                entityTypeId = context.entityTypeId,
                embedding = embedding,
                embeddingModel = embeddingProvider.getModelName(),
                schemaVersion = context.schemaVersion,
                truncated = truncated,
            )
        )

        val queueItem = ServiceUtil.findOrThrow { executionQueueRepository.findById(queueItemId) }
        executionQueueRepository.save(queueItem.copy(status = ExecutionQueueStatus.COMPLETED))

        logger.info { "Stored embedding for entity ${context.entityId}, queue item $queueItemId completed" }
    }

    // ------ Private Helpers ------

    /**
     * Returns true if the entity should be enqueued for embedding.
     *
     * INTEGRATION entities are skipped — they are synced from external systems
     * and should not be embedded directly.
     */
    private fun isEmbeddable(entity: riven.core.entity.entity.EntityEntity): Boolean =
        entity.sourceType != SourceType.INTEGRATION

    /**
     * Marks a queue item as CLAIMED with a timestamp.
     *
     * Idempotent: if already CLAIMED (Temporal activity retry scenario), updates the
     * claimedAt timestamp and saves again. This prevents duplicate processing while
     * allowing safe retries.
     */
    private fun claimQueueItem(queueItem: ExecutionQueueEntity): ExecutionQueueEntity {
        val claimed = queueItem.copy(
            status = ExecutionQueueStatus.CLAIMED,
            claimedAt = ZonedDateTime.now(),
        )
        return executionQueueRepository.save(claimed)
    }

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
     * the repository is only queried once per fetchContext call.
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

    // ------ Connotation Snapshot Persistence ------

    /**
     * Builds the [EntityMetadataSnapshot] from the freshly assembled [EnrichmentContext]
     * and upserts it to `entity_connotation` via [EntityConnotationRepository.upsertByEntityId].
     * RELATIONAL + STRUCTURAL metadata are populated deterministically; SENTIMENT carries the
     * outcome resolved by [resolveSentimentMetadata] (either an ANALYZED payload, a FAILED
     * sentinel, or NOT_APPLICABLE when the workspace/manifest hasn't opted in).
     */
    private fun persistConnotationSnapshot(
        entityId: UUID,
        workspaceId: UUID,
        entityType: EntityTypeEntity,
        context: EnrichmentContext,
        sentimentMetadata: SentimentMetadata,
    ) {
        val now = ZonedDateTime.now()
        val snapshot = EntityMetadataSnapshot(
            snapshotVersion = "v1",
            metadata = EntityMetadata(
                sentiment = sentimentMetadata,
                relational = buildRelationalMetadata(context, now),
                structural = buildStructuralMetadata(context, entityType, now),
            ),
            embeddedAt = now,
        )

        val snapshotJson = objectMapper.writeValueAsString(snapshot)
        entityConnotationRepository.upsertByEntityId(entityId, workspaceId, snapshotJson, now)

        logger.debug { "Persisted connotation snapshot for entity $entityId" }
    }

    /**
     * Resolves the SENTIMENT metadata for this enrichment cycle, gated on the workspace flag
     * and the entity type's manifest connotation signals.
     *
     * Returns a [SentimentMetadata] with [riven.core.enums.connotation.ConnotationStatus.NOT_APPLICABLE]
     * (default) when:
     * - the workspace has not opted in (`connotation_enabled = false`),
     * - the entity type has no manifest connotation signals (custom user-defined type or
     *   manifest entry omits the block),
     *
     * Otherwise delegates to [ConnotationAnalysisService] which returns either an ANALYZED
     * payload or a FAILED sentinel — both are persisted as-is so consumers can distinguish
     * "we tried and failed" from "we never tried".
     */
    private fun resolveSentimentMetadata(
        entityId: UUID,
        workspaceId: UUID,
        entityType: EntityTypeEntity,
    ): SentimentMetadata {
        val workspace = ServiceUtil.findOrThrow { workspaceRepository.findById(workspaceId) }
        if (!workspace.connotationEnabled) {
            return SentimentMetadata()
        }
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity must have an ID at enrichment time" }
        val signals = manifestCatalogService.getConnotationSignalsForEntityType(entityTypeId)
            ?: return SentimentMetadata()

        // Short-circuit when the manifest sentiment key has no mapping on this entity type:
        // there is nothing the analyzer could compute, so the correct status is NOT_APPLICABLE
        // (no mapping configured) rather than FAILED+MISSING_SOURCE_ATTRIBUTE (mapping exists
        // but value is null), which is what analyze() emits when the mapped attribute is empty.
        if (entityType.attributeKeyMapping?.containsKey(signals.sentimentAttribute) != true) {
            return SentimentMetadata()
        }

        val (sourceValue, themeValues) = resolveAttributeValues(entityId, entityType, signals)
        return connotationAnalysisService.analyze(
            entityId = entityId,
            workspaceId = workspaceId,
            signals = signals,
            sourceValue = sourceValue,
            themeValues = themeValues,
        )
    }

    /**
     * Resolves the manifest-keyed `sentimentAttribute` and `themeAttributes` to their
     * entity-level values via `entityType.attributeKeyMapping` + `entityAttributeService`.
     *
     * Caller [resolveSentimentMetadata] already short-circuits on a missing sentiment-key
     * mapping, so a null sourceValue here means the mapping exists but the underlying
     * attribute has no stored value — surfaced as MISSING_SOURCE_ATTRIBUTE downstream.
     * Theme attributes still tolerate missing keys (each is independently optional).
     */
    private fun resolveAttributeValues(
        entityId: UUID,
        entityType: EntityTypeEntity,
        signals: ConnotationSignals,
    ): Pair<Any?, Map<String, String?>> {
        val keyMapping = entityType.attributeKeyMapping ?: emptyMap()
        val attributesByUuid = entityAttributeService.getAttributes(entityId)

        fun valueForManifestKey(manifestKey: String): Any? {
            val attrUuidString = keyMapping[manifestKey] ?: return null
            val attrUuid = runCatching { UUID.fromString(attrUuidString) }.getOrNull() ?: return null
            return attributesByUuid[attrUuid]?.value
        }

        val sourceValue = valueForManifestKey(signals.sentimentAttribute)
        val themeValues = signals.themeAttributes.associateWith {
            valueForManifestKey(it)?.toString()
        }
        return sourceValue to themeValues
    }

    /**
     * Builds the RELATIONAL metadata snapshot from already-computed enrichment context.
     */
    private fun buildRelationalMetadata(context: EnrichmentContext, snapshotAt: ZonedDateTime): RelationalMetadata {
        val relationshipSummaries = context.relationshipSummaries.map { summary ->
            RelationshipSummarySnapshot(
                definitionId = summary.definitionId.toString(),
                definitionName = summary.relationshipName,
                count = summary.count,
                topCategories = summary.topCategories,
                latestActivityAt = summary.latestActivityAt,
            )
        }
        val clusterMembers = context.clusterMembers.map { member ->
            ClusterMemberSnapshot(
                sourceType = member.sourceType,
                entityTypeName = member.entityTypeName,
            )
        }
        val resolutions = context.referencedEntityIdentifiers.flatMap { (refEntityId, displayValue) ->
            context.attributes
                .filter {
                    it.classification == SemanticAttributeClassification.RELATIONAL_REFERENCE &&
                        it.value == refEntityId.toString()
                }
                .map { attr ->
                    RelationalReferenceResolution(
                        attributeId = attr.attributeId.toString(),
                        targetEntityId = refEntityId.toString(),
                        targetIdentifierValue = displayValue,
                    )
                }
        }
        return RelationalMetadata(
            relationshipSummaries = relationshipSummaries,
            clusterMembers = clusterMembers,
            relationalReferenceResolutions = resolutions,
            snapshotAt = snapshotAt,
        )
    }

    /**
     * Builds the STRUCTURAL metadata snapshot — entity type metadata, attribute classifications,
     * and relationship semantic definitions captured at embed time.
     */
    private fun buildStructuralMetadata(
        context: EnrichmentContext,
        entityType: EntityTypeEntity,
        snapshotAt: ZonedDateTime,
    ): StructuralMetadata {
        val attributeClassifications = context.attributes.map { attr ->
            AttributeClassificationSnapshot(
                attributeId = attr.attributeId.toString(),
                semanticLabel = attr.semanticLabel,
                classification = attr.classification,
                schemaType = attr.schemaType,
            )
        }
        val relationshipDefinitions = context.relationshipDefinitions.map { definition ->
            RelationshipSemanticDefinitionSnapshot(
                definitionName = definition.name,
                definitionText = definition.definition,
            )
        }
        return StructuralMetadata(
            entityTypeName = entityType.displayNameSingular,
            semanticGroup = entityType.semanticGroup,
            lifecycleDomain = entityType.lifecycleDomain,
            entityTypeDefinition = context.entityTypeDefinition,
            schemaVersion = entityType.version,
            attributeClassifications = attributeClassifications,
            relationshipSemanticDefinitions = relationshipDefinitions,
            snapshotAt = snapshotAt,
        )
    }
}
