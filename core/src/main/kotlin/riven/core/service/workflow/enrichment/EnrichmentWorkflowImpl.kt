package cranium.core.service.workflow.enrichment

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import cranium.core.service.enrichment.EmbeddingConsumer
import java.time.Duration
import java.util.UUID

/**
 * Implementation of [EnrichmentWorkflow] for the entity embedding pipeline.
 *
 * This class is NOT a Spring bean — Temporal manages its lifecycle. It is instantiated by
 * Temporal's worker via a factory in [cranium.core.configuration.workflow.TemporalWorkerConfiguration].
 *
 * DETERMINISM RULES:
 * - Uses Workflow.getLogger() for logging (NOT KLogger — not determinism-safe)
 * - Uses Workflow.newActivityStub() via createActivitiesStub() for all side effects
 * - No Spring injection — uses no-arg constructor
 * - No direct database access, no HTTP calls
 *
 * Activity options: startToCloseTimeout = 60s, 3 max attempts with exponential backoff.
 * The longer timeout vs identity-match reflects potential embedding API latency.
 *
 * CONSUMER MODEL (Plan 01-03 + PR feedback r3180290311):
 * - The **primary** consumer is [EmbeddingConsumer]. It owns the queue-completion path —
 *   `embedAndStore` is what marks the row COMPLETED on success. A terminal failure here
 *   triggers an explicit `markQueueItemFailed` activity so the row transitions to FAILED
 *   rather than being silently swallowed and left CLAIMED forever.
 * - The **sibling** list ([buildConsumers]) is for downstream non-completion consumers
 *   (Phase 1 ships none; future: synthesis, JSONB projection). Each sibling is wrapped in
 *   [runCatching] per ENRICH-05 — a sibling's terminal failure must NOT block its siblings
 *   and must NOT regress the queue row.
 *
 * The pre-r3180290311 shape iterated all consumers (including the embedding one) under a
 * single `runCatching`, which left the queue stuck in CLAIMED with no FAILED state when the
 * embedding consumer exhausted retries. The split here closes that gap.
 */
open class EnrichmentWorkflowImpl : EnrichmentWorkflow {

    private val logger = Workflow.getLogger(EnrichmentWorkflowImpl::class.java)

    /**
     * Orchestrates the enrichment pipeline: analyze semantics, run the primary completion
     * consumer (with explicit FAILED transition on terminal failure), then fan out to siblings.
     *
     * Primary consumer ([EmbeddingConsumer]) failure semantics: caught at the workflow level,
     * logged as a warning, and recorded on the queue row via the `markQueueItemFailed` activity.
     * The workflow itself does NOT propagate the exception — Temporal sees a successful workflow
     * with a FAILED queue row, which downstream observers can act on.
     *
     * Sibling consumer failure semantics ([buildConsumers]): wrapped in [runCatching] per
     * ENRICH-05; the queue row is unaffected by sibling failures.
     *
     * See class KDoc for the behavior delta vs. the pre-r3180290311 workflow.
     */
    override fun embed(queueItemId: UUID) {
        logger.info("Starting enrichment pipeline for queueItemId=$queueItemId")

        val stub = createActivitiesStub()
        val context = stub.analyzeSemantics(queueItemId)
        logger.info("Analyzed semantics for queueItemId=$queueItemId entityId=${context.entityId}")

        runCatching { buildPrimaryConsumer(stub).run(context, queueItemId) }
            .onFailure { e ->
                logger.warn(
                    "Primary EmbeddingConsumer failed for queueItemId=$queueItemId: ${e.message}; marking queue FAILED"
                )
                stub.markQueueItemFailed(queueItemId, e.message ?: "unknown")
            }

        val siblings: List<ConsumerActivity> = buildConsumers(stub)
        siblings.forEach { consumer ->
            runCatching { consumer.run(context, queueItemId) }
                .onFailure { e ->
                    logger.warn(
                        "Sibling consumer ${consumer::class.simpleName} failed for queueItemId=$queueItemId: ${e.message}"
                    )
                }
        }

        logger.info("Enrichment pipeline complete for queueItemId=$queueItemId entityId=${context.entityId}")
    }

    /**
     * Creates the activity stub with retry and timeout configuration.
     *
     * startToCloseTimeout = 60s to accommodate embedding API latency.
     * 3 max attempts with exponential backoff (2s -> 4s -> 30s cap).
     *
     * Internal open to allow test subclasses to inject mock activity stubs without
     * requiring a live Temporal execution context.
     */
    internal open fun createActivitiesStub(): EnrichmentActivities =
        Workflow.newActivityStub(
            EnrichmentActivities::class.java,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(60))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofSeconds(30))
                        .build()
                )
                .build()
        )

    /**
     * Builds the **primary** completion consumer — the one that owns the queue-completion path.
     *
     * Phase 1 returns [EmbeddingConsumer]; this is the consumer whose terminal failure must
     * transition the queue row to FAILED via [EnrichmentActivities.markQueueItemFailed].
     *
     * Internal open to allow test subclasses to inject a test-double primary without requiring
     * a live Temporal activity stub.
     */
    internal open fun buildPrimaryConsumer(stub: EnrichmentActivities): ConsumerActivity =
        EmbeddingConsumer(stub)

    /**
     * Builds the list of **sibling** post-analyze consumers — non-completion consumers whose
     * failures are isolated per ENRICH-05 and do NOT mutate the queue row state.
     *
     * Phase 1 ships zero siblings. Future phases (Synthesis, JSONB projection) add entries here
     * without modifying the orchestration method.
     *
     * Internal open to allow test subclasses to inject test-double siblings without requiring
     * a live Temporal activity stub.
     */
    internal open fun buildConsumers(stub: EnrichmentActivities): List<ConsumerActivity> =
        emptyList()
}
