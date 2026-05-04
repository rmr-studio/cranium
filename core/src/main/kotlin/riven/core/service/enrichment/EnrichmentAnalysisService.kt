package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.entity.semantics.SemanticAttributeClassification
import riven.core.enums.workflow.ExecutionQueueStatus
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
import riven.core.models.enrichment.EnrichmentContext
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.connotation.ConnotationAnalysisService
import riven.core.service.entity.EntityAttributeService
import riven.core.util.ServiceUtil
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Service responsible for the semantic analysis phase of the enrichment pipeline.
 *
 * Owns the analysis half of the pipeline: claims the queue item, resolves sentiment metadata
 * (gated on workspace opt-in and manifest configuration), delegates context assembly to
 * [EnrichmentContextAssembler], persists the polymorphic connotation snapshot, and returns
 * a transient [EnrichmentContext] for downstream consumer activities.
 *
 * Extracted from [EnrichmentService] in Plan 01-03 as the final decomposition step.
 * [EnrichmentService] is deleted in the same plan once this service is wired.
 *
 * **Constructor dep count:** 10 (ceiling ≤ 10 per ENRICH-02 — this is the max allowed).
 * Sentiment + manifest helpers stayed on this service per 01-CONTEXT byte-identity precedence decision.
 *
 * **Workspace lookup:** Uses [WorkspaceRepository] directly rather than [riven.core.service.workspace.WorkspaceService].
 * Plan 01-CONTEXT explicitly resolved this: WorkspaceService carries `@PreAuthorize` + exception-mapping
 * that would alter exception-thrown shape on missing-workspace (AccessDeniedException vs NotFoundException
 * from `findOrThrow`), breaking the byte-identical snapshot gate (ENRICH-03). Phase 2 may revisit when
 * the snapshot lifecycle ends.
 *
 * **Concurrency posture:** snapshot persistence uses an atomic
 * `INSERT ... ON CONFLICT (entity_id) DO UPDATE` keyed by `entity_id`, so concurrent writers
 * always converge to a single row and race only for last-write-wins on the payload. Each writer's
 * own view is internally consistent at fetch time; the surviving row reflects whichever transaction
 * commits last.
 */
@Service
class EnrichmentAnalysisService(
    private val enrichmentContextAssembler: EnrichmentContextAssembler,
    private val executionQueueRepository: ExecutionQueueRepository,
    private val entityRepository: EntityRepository,
    private val entityTypeRepository: EntityTypeRepository,
    private val entityConnotationRepository: EntityConnotationRepository,
    private val entityAttributeService: EntityAttributeService,
    private val connotationAnalysisService: ConnotationAnalysisService,
    private val workspaceRepository: WorkspaceRepository,
    private val manifestCatalogService: ManifestCatalogService,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger,
) {

    // ------ Public API ------

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
     * @throws riven.core.exceptions.NotFoundException if the queue item does not exist
     */
    @Transactional
    fun analyzeSemantics(queueItemId: UUID): EnrichmentContext {
        val queueItem = ServiceUtil.findOrThrow { executionQueueRepository.findById(queueItemId) }

        val claimedItem = claimQueueItem(queueItem)
        val entityId = requireNotNull(claimedItem.entityId) { "ENRICHMENT queue item must have an entityId" }

        val entity = ServiceUtil.findOrThrow { entityRepository.findById(entityId) }
        val entityType = ServiceUtil.findOrThrow { entityTypeRepository.findById(entity.typeId) }

        val sentimentMetadata: SentimentMetadata = resolveSentimentMetadata(entityId, entity.workspaceId, entityType)

        val context = enrichmentContextAssembler.assemble(
            entityId = entityId,
            workspaceId = entity.workspaceId,
            queueItemId = queueItemId,
            entityTypeId = entity.typeId,
            schemaVersion = entityType.version,
            entityTypeName = entityType.displayNameSingular,
            semanticGroup = entityType.semanticGroup,
            lifecycleDomain = entityType.lifecycleDomain,
            sentiment = if (sentimentMetadata.status == ConnotationStatus.ANALYZED) sentimentMetadata else null,
        )

        persistConnotationSnapshot(entityId, entity.workspaceId, entityType, context, sentimentMetadata)

        return context
    }

    // ------ Private Helpers ------

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
     * - the manifest sentiment key has no mapping on this entity type.
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
