package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.stereotype.Service
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.models.entity.knowledge.TruncationBudget
import riven.core.models.entity.knowledge.TruncationResult

/**
 * Phase 2 stub — Phase 3 PROJ-01..PROJ-06 implements sequential phase methods over MutableProjectionState.
 *
 * Converts an [EntityKnowledgeView] to embedding text within a [TruncationBudget]. The Phase 3
 * implementation will execute the following truncation phases in order:
 * 1. protectIdentity — always emits identity and type narrative (uncapped)
 * 2. dropFreetextIfOverBudget — drops freetext attributes if over budget
 * 3. dropRelationalRefsIfOverBudget — drops relational references if over budget
 * 4. trimKnowledgeByRecency — trims knowledge backlinks oldest-first if still over budget
 *
 * knowledgeBacklinkCap from [riven.core.configuration.properties.EnrichmentConfigurationProperties]
 * is applied at the assembler boundary (Plan 02-02), not here.
 *
 * This stub is injected by Phase 3 without further wiring changes — the constructor signature
 * and method signature are the stable contract surface.
 */
@Service
class EntityKnowledgeViewProjector(
    private val logger: KLogger,
) {

    /**
     * Converts [view] to embedding text within [budget].
     *
     * Phase 2 stub — always returns empty text and empty telemetry.
     * Phase 3 PROJ-01..PROJ-06 replaces this body with sequential truncation phase calls.
     */
    fun toEmbeddingText(view: EntityKnowledgeView, budget: TruncationBudget): TruncationResult {
        // Phase 3 implements protectIdentity / dropFreetextIfOverBudget /
        // dropRelationalRefsIfOverBudget / trimKnowledgeByRecency.
        logger.debug { "Projector stub invoked for entity ${view.entityId} — Phase 3 implements truncation" }
        return TruncationResult(text = "", telemetry = emptyList())
    }
}
