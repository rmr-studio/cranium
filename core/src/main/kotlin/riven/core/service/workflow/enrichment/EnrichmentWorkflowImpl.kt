package riven.core.service.workflow.enrichment

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
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
 */
open class EnrichmentWorkflowImpl : EnrichmentWorkflow {

    private val logger = Workflow.getLogger(EnrichmentWorkflowImpl::class.java)

    override fun embed(queueItemId: UUID) {
        logger.info("Starting enrichment pipeline for queueItemId=$queueItemId")

        val stub = createActivitiesStub()

        val context = stub.fetchEntityContext(queueItemId)
        logger.info("Fetched entity context for queueItemId=$queueItemId entityId=${context.entityId}")

        val result = stub.constructEnrichedText(context)
        logger.info("Constructed enriched text for queueItemId=$queueItemId length=${result.text.length} truncated=${result.truncated}")

        val embedding = stub.generateEmbedding(result.text)
        logger.info("Generated embedding for queueItemId=$queueItemId dimensions=${embedding.size}")

        stub.storeEmbedding(queueItemId, context, embedding, result.truncated)
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
}
