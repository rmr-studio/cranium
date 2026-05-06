package riven.core.service.workflow.enrichment

import riven.core.models.entity.knowledge.EntityKnowledgeView
import java.util.UUID

/**
 * Consumer fan-out point for the enrichment pipeline.
 *
 * Each consumer (Phase 1: embedding only; future: synthesis, JSONB projection) is invoked
 * sequentially by [EnrichmentWorkflowImpl.embed] AFTER the analysis activity completes,
 * each as its own Temporal activity (Decision 4-ii.A). The workflow wraps each `run`
 * call so a single consumer's failure does not block siblings — see plan 01-03 for the
 * `runCatching` semantics and the documented behavior delta vs. the pre-Phase-1 workflow.
 *
 * SAM-friendly: lambda construction is required at the workflow site, but each concrete
 * implementation in production code (e.g. `EmbeddingConsumer`) lives as a named class so
 * Temporal's activity registration mechanism can address it by type.
 */
fun interface ConsumerActivity {
    fun run(view: EntityKnowledgeView, queueItemId: UUID)
}
