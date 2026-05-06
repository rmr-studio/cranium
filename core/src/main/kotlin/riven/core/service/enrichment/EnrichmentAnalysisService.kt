package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.entity.semantics.SemanticAttributeClassification
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
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.util.ServiceUtil
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Service responsible for the semantic analysis phase of the enrichment pipeline.
 *
 * Plan 02-03 shrink: claims the queue item, delegates context assembly to
 * [EnrichmentContextAssembler] (which now returns [EntityKnowledgeView] directly),
 * persists the polymorphic connotation snapshot from view sections, and returns
 * the view for downstream consumer activities.
 *
 * Sentiment resolution moved to [SentimentResolutionService] (Plan 02-02) and called from
 * inside [EnrichmentContextAssembler.assemble]. This service no longer owns sentiment logic.
 *
 * **Constructor dep count:** 6 (assembler, executionQueueRepository, entityRepository,
 * entityConnotationRepository, objectMapper, logger). Well within the 12-dep ceiling (ENRICH-02).
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
    private val entityConnotationRepository: EntityConnotationRepository,
    private val objectMapper: ObjectMapper,
    private val logger: KLogger,
) {

    // ------ Public API ------

    /**
     * Claims a queue item, assembles the [EntityKnowledgeView] via [EnrichmentContextAssembler],
     * persists the polymorphic connotation snapshot from view sections, and returns the view
     * for downstream consumer activities.
     *
     * Claim transition is performed by an atomic compare-and-set in
     * [ExecutionQueueRepository.claimEnrichmentItem] that restricts the legal pre-state to
     * `job_type = ENRICHMENT` and `status` in `{PENDING, CLAIMED}`. Already-COMPLETED or
     * already-FAILED rows cannot be regressed; concurrent claims of the same PENDING row
     * resolve via row-level locking — only one transaction wins. Allowing CLAIMED -> CLAIMED
     * keeps the activity safely retryable.
     *
     * @param queueItemId The enrichment queue row to process
     * @return Complete [EntityKnowledgeView] for downstream activities
     * @throws IllegalStateException if the queue item cannot be claimed (missing, wrong job
     *   type, or already in a terminal state)
     * @throws riven.core.exceptions.NotFoundException if the queue item disappears between
     *   the CAS and the re-fetch
     */
    @Transactional
    fun analyzeSemantics(queueItemId: UUID): EntityKnowledgeView {
        claimQueueItem(queueItemId)
        val queueItem = ServiceUtil.findOrThrow { executionQueueRepository.findById(queueItemId) }
        val entityId = requireNotNull(queueItem.entityId) { "ENRICHMENT queue item must have an entityId" }
        val entity = ServiceUtil.findOrThrow { entityRepository.findById(entityId) }

        val view = enrichmentContextAssembler.assemble(entityId, entity.workspaceId, queueItemId)

        persistConnotationSnapshot(view)
        return view
    }

    // ------ Private Helpers ------

    /**
     * Atomically transitions the queue item to CLAIMED via the repository CAS.
     *
     * Throws [IllegalStateException] when the row count is not 1 — meaning the row is
     * missing, has a non-ENRICHMENT job type, or is already in a terminal
     * (`COMPLETED`/`FAILED`) state. This prevents a retried analyze activity from
     * regressing a row that the embedding consumer has already completed or failed.
     */
    private fun claimQueueItem(queueItemId: UUID) {
        val rowCount = executionQueueRepository.claimEnrichmentItem(queueItemId, ZonedDateTime.now())
        check(rowCount == 1) {
            "Cannot claim execution_queue row $queueItemId: " +
                "row missing, wrong job_type, or status not in {PENDING, CLAIMED}"
        }
    }

    // ------ Connotation Snapshot Persistence ------

    /**
     * Builds the [EntityMetadataSnapshot] from the assembled [EntityKnowledgeView] and upserts
     * it to `entity_connotation` via [EntityConnotationRepository.upsertByEntityId].
     *
     * SENTIMENT: sourced from [EntityKnowledgeView.sections.entityMetadata.sentiment] — populated
     * by [SentimentResolutionService] inside [EnrichmentContextAssembler.assemble]. Null when the
     * workspace has not opted in or the entity type has no manifest connotation signals;
     * in that case [SentimentMetadata.notApplicable] is used as the placeholder.
     *
     * RELATIONAL + STRUCTURAL metadata are derived from the view sections and are semantically
     * equivalent to Phase 1's snapshot (byte-identity NOT required — the Phase 1 snapshot test
     * is deleted in Plan 02-03).
     */
    private fun persistConnotationSnapshot(view: EntityKnowledgeView) {
        val now = ZonedDateTime.now()
        // When no analyzed sentiment, use the NOT_APPLICABLE default via the no-arg constructor
        val sentimentForSnapshot = view.sections.entityMetadata.sentiment
            ?: SentimentMetadata()

        val snapshot = EntityMetadataSnapshot(
            snapshotVersion = "v1",
            metadata = EntityMetadata(
                sentiment = sentimentForSnapshot,
                relational = buildRelationalMetadata(view, now),
                structural = buildStructuralMetadata(view, now),
            ),
            embeddedAt = now,
        )

        val snapshotJson = objectMapper.writeValueAsString(snapshot)
        entityConnotationRepository.upsertByEntityId(view.entityId, view.workspaceId, snapshotJson, now)

        logger.debug { "Persisted connotation snapshot for entity ${view.entityId}" }
    }

    /**
     * Builds RELATIONAL metadata from view sections.
     * - Relationship summaries from [EntityKnowledgeView.sections.catalogBacklinks]
     * - Cluster members from [EntityKnowledgeView.sections.clusterSiblings]
     * - Relational reference resolutions from [EntityKnowledgeView.sections.relationalReferences]
     */
    private fun buildRelationalMetadata(view: EntityKnowledgeView, snapshotAt: ZonedDateTime): RelationalMetadata {
        val relationshipSummaries = view.sections.catalogBacklinks.map { backlink ->
            RelationshipSummarySnapshot(
                definitionId = backlink.definitionId.toString(),
                definitionName = backlink.relationshipName,
                count = backlink.count,
                topCategories = emptyList(), // Phase 2: topCategories dropped from CatalogBacklinkSection
                latestActivityAt = backlink.latestActivityAt,
            )
        }
        val clusterMembers = view.sections.clusterSiblings.map { sibling ->
            ClusterMemberSnapshot(
                // sourceType stored as String in ClusterSiblingSection; convert via SourceType.valueOf
                sourceType = riven.core.enums.integration.SourceType.valueOf(sibling.sourceType),
                entityTypeName = sibling.entityTypeName,
            )
        }
        val resolutions = view.sections.relationalReferences.map { ref ->
            // Find the attribute that holds this reference via the attributes section
            val matchingAttr = view.sections.attributes.firstOrNull {
                it.classification == SemanticAttributeClassification.RELATIONAL_REFERENCE &&
                    it.value == ref.referencedEntityId.toString()
            }
            RelationalReferenceResolution(
                attributeId = matchingAttr?.attributeId?.toString() ?: ref.referencedEntityId.toString(),
                targetEntityId = ref.referencedEntityId.toString(),
                targetIdentifierValue = ref.displayValue,
            )
        }
        return RelationalMetadata(
            relationshipSummaries = relationshipSummaries,
            clusterMembers = clusterMembers,
            relationalReferenceResolutions = resolutions,
            snapshotAt = snapshotAt,
        )
    }

    /**
     * Builds STRUCTURAL metadata from view sections.
     * - Entity type name, semantic group, lifecycle domain from [EntityKnowledgeView.sections.typeNarrative]
     * - Attribute classifications from [EntityKnowledgeView.sections.attributes]
     * - Schema version from [EntityKnowledgeView.schemaVersion]
     *
     * Note: relationship semantic definitions section removed from Phase 2 view model;
     * glossary definitions now live in typeNarrative.glossaryDefinitions.
     */
    private fun buildStructuralMetadata(view: EntityKnowledgeView, snapshotAt: ZonedDateTime): StructuralMetadata {
        val narrative = view.sections.typeNarrative
        val attributeClassifications = view.sections.attributes.map { attr ->
            AttributeClassificationSnapshot(
                attributeId = attr.attributeId.toString(),
                semanticLabel = attr.semanticLabel,
                classification = attr.classification,
                schemaType = attr.schemaType,
            )
        }
        // Phase 2: glossary definitions in typeNarrative.glossaryDefinitions;
        // not yet surfaced as RelationshipSemanticDefinitionSnapshot (Phase 3 PROJ-12 concern).
        val relationshipDefinitions: List<RelationshipSemanticDefinitionSnapshot> = emptyList()

        return StructuralMetadata(
            entityTypeName = narrative.entityTypeName,
            semanticGroup = narrative.semanticGroup,
            lifecycleDomain = narrative.lifecycleDomain,
            entityTypeDefinition = narrative.metadataDefinition,
            schemaVersion = view.schemaVersion,
            attributeClassifications = attributeClassifications,
            relationshipSemanticDefinitions = relationshipDefinitions,
            snapshotAt = snapshotAt,
        )
    }
}
