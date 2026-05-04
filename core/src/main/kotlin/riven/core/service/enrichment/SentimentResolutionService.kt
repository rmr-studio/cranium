package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.entity.entity.EntityTypeEntity
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.connotation.SentimentMetadata
import riven.core.models.entity.payload.EntityAttributePrimitivePayload
import riven.core.repository.workspace.WorkspaceRepository
import riven.core.service.catalog.ManifestCatalogService
import riven.core.service.connotation.ConnotationAnalysisService
import riven.core.service.entity.EntityAttributeService
import riven.core.util.ServiceUtil
import java.util.UUID

/**
 * Resolves sentiment metadata for an entity during the enrichment pipeline.
 *
 * Extracted from [EnrichmentAnalysisService] in Plan 02-02 to keep the assembler
 * constructor count under the 12-parameter ceiling (Decision 2A / ENRICH-02).
 *
 * Owns the sentiment-resolution logic: workspace opt-in gate, manifest signals gate,
 * attribute key mapping short-circuit, and ConnotationAnalysisService delegation.
 * This logic is identical to the inline private `resolveSentimentMetadata` in
 * [EnrichmentAnalysisService] — it is LIFTED here verbatim. [EnrichmentAnalysisService]
 * still retains its own copy through Plan 02-02; Plan 02-03 removes the duplicate
 * when it wires [EnrichmentAnalysisService] to delegate here instead.
 *
 * **Workspace lookup:** Uses [WorkspaceRepository] directly rather than [WorkspaceService].
 * This matches the Plan 01-CONTEXT precedent that established WorkspaceRepository usage for
 * byte-identical snapshot gating (ENRICH-03). Phase 2 may revisit when the snapshot lifecycle ends.
 */
@Service
class SentimentResolutionService(
    private val connotationAnalysisService: ConnotationAnalysisService,
    private val workspaceRepository: WorkspaceRepository,
    private val manifestCatalogService: ManifestCatalogService,
    private val entityAttributeService: EntityAttributeService,
    private val logger: KLogger,
) {

    // ------ Public API ------

    /**
     * Resolves the SENTIMENT metadata for an entity enrichment cycle.
     *
     * Returns a [SentimentMetadata] with [riven.core.enums.connotation.ConnotationStatus.NOT_APPLICABLE]
     * when:
     * - the workspace has not opted in (`connotation_enabled = false`),
     * - the entity type has no manifest connotation signals (custom user-defined type or
     *   manifest entry omits the block),
     * - the manifest sentiment key has no mapping on this entity type.
     *
     * Otherwise delegates to [ConnotationAnalysisService] which returns either an ANALYZED
     * payload or a FAILED sentinel — both are passed through so downstream consumers can
     * distinguish "we tried and failed" from "we never tried".
     *
     * @param entityId The entity being enriched
     * @param workspaceId The workspace the entity belongs to
     * @param entityType The fully loaded entity type entity (identifier key mapping is read here)
     * @return Resolved sentiment metadata (never null)
     */
    fun resolve(entityId: UUID, workspaceId: UUID, entityType: EntityTypeEntity): SentimentMetadata {
        val workspace = ServiceUtil.findOrThrow { workspaceRepository.findById(workspaceId) }
        if (!workspace.connotationEnabled) {
            return SentimentMetadata()
        }
        val entityTypeId = requireNotNull(entityType.id) { "EntityTypeEntity must have an ID at enrichment time" }
        val signals = manifestCatalogService.getConnotationSignalsForEntityType(entityTypeId)
            ?: return SentimentMetadata()

        // Short-circuit when the manifest sentiment key has no mapping on this entity type:
        // there is nothing the analyzer could compute, so the correct status is NOT_APPLICABLE.
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

    // ------ Private Helpers ------

    /**
     * Resolves the manifest-keyed `sentimentAttribute` and `themeAttributes` to their
     * entity-level values via `entityType.attributeKeyMapping` + `entityAttributeService`.
     *
     * Caller [resolve] already short-circuits on a missing sentiment-key mapping, so a null
     * sourceValue here means the mapping exists but the underlying attribute has no stored value.
     * Theme attributes tolerate missing keys (each is independently optional).
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
        val themeValues = signals.themeAttributes.associateWith { valueForManifestKey(it)?.toString() }
        return sourceValue to themeValues
    }
}
