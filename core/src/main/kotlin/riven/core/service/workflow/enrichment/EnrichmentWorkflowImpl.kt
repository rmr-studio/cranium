package riven.core.service.workflow.enrichment

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import riven.core.service.enrichment.EmbeddingConsumer
import java.time.Duration
import java.util.UUID

/**
 * Implementation of [EnrichmentWorkflow] for the entity embedding pipeline.
 *
 * This class is NOT a Spring bean — Temporal manages its lifecycle. It is instantiated by
 * Temporal's worker via a factory in [riven.core.configuration.workflow.TemporalWorkerConfiguration].
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
 * BEHAVIOR DELTA (Plan 01-03, Decision 4-ii.A): Each consumer's [ConsumerActivity.run] is wrapped
 * in [runCatching]. This means a consumer's terminal failure (after all Temporal retries are
 * exhausted within the activity) is logged as a warning but does NOT propagate as a workflow failure.
 * Pre-Phase-1 behavior: an embedding failure propagated and failed the entire workflow.
 * Phase-1 behavior: embedding failure is swallowed at the workflow level so future sibling consumers
 * (Synthesis, JSONB projection) can execute independently. This is the necessary semantic for
 * ENRICH-05 ("one consumer's terminal failure must not block siblings").
 */
open class EnrichmentWorkflowImpl : EnrichmentWorkflow {

    private val logger = Workflow.getLogger(EnrichmentWorkflowImpl::class.java)

    /**
     * Orchestrates the enrichment pipeline: analyze semantics, then fan out to each consumer.
     *
     * Each consumer (Phase 1: [EmbeddingConsumer] only; future: synthesis, JSONB projection)
     * receives the assembled [riven.core.models.enrichment.EnrichmentContext] and runs as its own
     * Temporal activity via [ConsumerActivity.run]. Consumers are iterated sequentially; each is
     * wrapped in [runCatching] so a single consumer's terminal failure does not block siblings.
     *
     * See class KDoc for the behavior delta vs. the pre-Phase-1 workflow.
     */
    override fun embed(queueItemId: UUID) {
        logger.info("Starting enrichment pipeline for queueItemId=$queueItemId")

        val stub = createActivitiesStub()
        val context = stub.analyzeSemantics(queueItemId)
        logger.info("Analyzed semantics for queueItemId=$queueItemId entityId=${context.entityId}")

        val consumers: List<ConsumerActivity> = buildConsumers(stub)
        consumers.forEach { consumer ->
            runCatching { consumer.run(context, queueItemId) }
                .onFailure { e ->
                    logger.warn("Consumer ${consumer::class.simpleName} failed for queueItemId=$queueItemId: ${e.message}")
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
     * Builds the list of post-analyze consumers.
     *
     * Phase 1 ships exactly one consumer ([EmbeddingConsumer]). Future phases add siblings
     * (Synthesis, JSONB projection) without modifying this orchestration method.
     *
     * Internal open to allow test subclasses to inject test-double consumers without
     * requiring a live Temporal activity stub.
     */
    internal open fun buildConsumers(stub: EnrichmentActivities): List<ConsumerActivity> =
        listOf(EmbeddingConsumer(stub))
}
