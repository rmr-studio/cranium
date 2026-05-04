package riven.core.service.workflow.enrichment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.temporal.activity.ActivityOptions
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowOptions
import io.temporal.common.RetryOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.Worker
import io.temporal.workflow.Workflow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import riven.core.configuration.workflow.TemporalWorkerConfiguration
import riven.core.models.enrichment.EnrichmentContext
import riven.core.service.enrichment.EmbeddingConsumer
import riven.core.service.util.factory.EnrichmentFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Workflow-shape integration test for [EnrichmentWorkflowImpl] — proves the three ENRICH-05
 * consumer-independence contracts end-to-end using Temporal's [TestWorkflowEnvironment].
 *
 * **What this IT covers:**
 * 1. Embedding terminal failure does not retry analysis nor propagate as a workflow failure.
 * 2. Analysis success persists the semantic snapshot regardless of embedding outcome
 *    (verified by proxy: analyzeSemantics stub returning a context stands in for the
 *    real [riven.core.service.enrichment.EnrichmentAnalysisService.analyzeSemantics] side-effect,
 *    which is unit-tested separately in [riven.core.service.enrichment.EnrichmentAnalysisServiceTest]).
 * 3. The workflow scaffold accepts a [List]<[ConsumerActivity]> of size > 1 — injecting a second
 *    test-only consumer verifies that both run (embedding AND the test consumer).
 * 4. A non-embedding consumer failure is also swallowed — the embedding consumer still runs.
 *
 * **Lifecycle pattern:** [Nested] inner classes — each nested class owns its [BeforeEach] so the
 * consumer-list shape is determined before [TestWorkflowEnvironment] construction. This avoids
 * the ordering brittleness of a parameterised setUp helper where extraConsumers must be
 * initialised before calling setUp but after JUnit has set up the outer class.
 *
 * **Activity stub pattern:** Temporal's `registerActivitiesImplementations` rejects Mockito-generated
 * dynamic proxy classes because those proxies inherit `@ActivityMethod` annotations from the interface,
 * which Temporal only permits on the original interface method. The workaround is [DelegatingActivitiesImpl] —
 * a concrete class that implements [EnrichmentActivities] and delegates to an inner [Delegate] interface.
 * Tests configure the [Delegate] (a plain Mockito mock of a non-annotated interface) and inspect it via
 * verify() after the workflow run.
 *
 * **No Spring context:** [TestWorkflowEnvironment] is pure Temporal — no Spring Boot test context,
 * no H2, no Testcontainers. Service-side persistence is covered by per-service unit tests.
 *
 * **Temporal testing dependency:** Uses temporal-testing:1.34.0 (aligned with temporal-sdk:1.34.0).
 * The previously pinned 1.24.1 version caused version-mismatch hanging; upgraded as part of Plan 01-04.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class EnrichmentWorkflowIT {

    // ---- Shared fixture context (deterministic UUID, factory-built) ----
    private val queueItemId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val fixtureContext: EnrichmentContext = EnrichmentFactory.createEnrichmentContext(
        queueItemId = queueItemId,
    )

    companion object {
        /**
         * Creates a [TestWorkflowEnvironment] with a Kotlin-aware Jackson data converter.
         *
         * Temporal's default [TestWorkflowEnvironment.newInstance()] uses a bare
         * [JacksonJsonPayloadConverter] without the Kotlin module, which cannot deserialize
         * Kotlin data classes (no no-arg constructor). This helper overrides only the Jackson
         * converter in the default converter chain (keeping NullPayloadConverter,
         * ByteArrayPayloadConverter, etc.) by registering [KotlinModule] so Temporal can
         * round-trip [EnrichmentContext] and its nested data classes through JSON serialization
         * between the workflow and activity sides of the test environment.
         */
        fun newTestEnvironment(): TestWorkflowEnvironment {
            // findAndRegisterModules() auto-discovers KotlinModule on the runtime classpath
            // (com.fasterxml.jackson.module:jackson-module-kotlin brought in by temporal-sdk)
            // plus JavaTimeModule for ZonedDateTime serialization in SentimentMetadata.
            val mapper = ObjectMapper()
                .findAndRegisterModules()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            val dataConverter = DefaultDataConverter.newDefaultInstance()
                .withPayloadConverterOverrides(JacksonJsonPayloadConverter(mapper))
            val clientOptions = WorkflowClientOptions.newBuilder()
                .setDataConverter(dataConverter)
                .build()
            val options = TestEnvironmentOptions.newBuilder()
                .setWorkflowClientOptions(clientOptions)
                .build()
            return TestWorkflowEnvironment.newInstance(options)
        }
    }

    // ------ WithEmbeddingConsumerOnly: Tests 1 and 2 ------

    /**
     * Tests run with exactly one consumer (the [EmbeddingConsumer]), matching the Phase 1
     * production configuration. This validates the core consumer-independence contracts.
     */
    @Nested
    inner class WithEmbeddingConsumerOnly {

        private lateinit var environment: TestWorkflowEnvironment
        private lateinit var delegate: ActivityDelegate

        @BeforeEach
        fun setUp() {
            delegate = mock()
            val activitiesImpl = DelegatingActivitiesImpl(delegate)

            environment = newTestEnvironment()
            val worker: Worker = environment.newWorker(TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
            // No extra consumers — matches Phase 1 production shape
            worker.registerWorkflowImplementationFactory(EnrichmentWorkflow::class.java) {
                TestEnrichmentWorkflowImpl(extraConsumers = emptyList())
            }
            worker.registerActivitiesImplementations(activitiesImpl)
            environment.start()
        }

        @AfterEach
        fun tearDown() {
            if (::environment.isInitialized) environment.close()
        }

        /**
         * ENRICH-05 Contract 1: embedding terminal failure (after all Temporal retries) does NOT
         * propagate as a workflow failure — the workflow completes successfully.
         *
         * ENRICH-05 Contract 2: analysis is NOT retried when embedding fails terminally — the
         * `verify(times(1)).analyzeSemantics` assertion directly proves the analysis side ran
         * exactly once with no rollback or re-invocation.
         *
         * Setup: analyzeSemantics returns the fixture context (simulating successful analysis +
         * implicit persistence via the real service's side-effect). embedAndStore always throws,
         * forcing Temporal to exhaust all 3 retry attempts before giving up. The workflow's
         * runCatching wrapper then swallows the terminal failure.
         *
         * Covers: ENRICH-05 sub-contracts (a) analysis-persists-regardless and
         * (b) embedding-failure-doesn't-retry-analysis.
         */
        @Test
        fun `embedding terminal failure marks queue FAILED does not retry analysis nor propagate to workflow`() {
            whenever(delegate.analyzeSemantics(queueItemId)).thenReturn(fixtureContext)
            whenever(delegate.embedAndStore(any(), eq(queueItemId)))
                .thenThrow(RuntimeException("simulated embedding failure"))

            // Workflow must complete without throwing — primary catch arm marks FAILED and does NOT propagate
            assertDoesNotThrow { executeWorkflow() }

            // Analysis ran exactly once — embedding failure did NOT cause analysis to be retried
            verify(delegate, times(1)).analyzeSemantics(queueItemId)
            // Temporal retried embedAndStore 3 times (maxAttempts = 3 in TestEnrichmentWorkflowImpl)
            // before giving up; each attempt is a separate activity invocation
            verify(delegate, times(3)).embedAndStore(any(), eq(queueItemId))
            // r3180290311 contract: terminal embedding failure transitions the queue row to FAILED
            // via the markQueueItemFailed activity. Without this, the queue would be stuck CLAIMED.
            verify(delegate, times(1)).markQueueItemFailed(eq(queueItemId), any())
        }

        /**
         * ENRICH-05 Contract 2 explicit assertion: analysis success persists the semantic snapshot
         * regardless of embedding outcome.
         *
         * The analyzeSemantics stub returning the fixture context is the proxy for "persistence
         * happened" — the real [riven.core.service.enrichment.EnrichmentAnalysisService.analyzeSemantics]
         * upserts the `entity_connotation` snapshot before returning the context. The workflow's
         * runCatching semantics ensure the snapshot is never rolled back when embedding fails.
         *
         * Covers: ENRICH-05 sub-contract (a) analysis-persists-regardless.
         */
        @Test
        fun `analysis success persists snapshot regardless of embedding outcome`() {
            whenever(delegate.analyzeSemantics(queueItemId)).thenReturn(fixtureContext)
            whenever(delegate.embedAndStore(any(), eq(queueItemId)))
                .thenThrow(RuntimeException("embedding outage"))

            assertDoesNotThrow { executeWorkflow() }

            // analyzeSemantics was invoked exactly once — by proxy: the semantic snapshot was persisted;
            // no rollback or re-invocation was triggered by the embedding failure
            verify(delegate, times(1)).analyzeSemantics(queueItemId)
        }

        private fun executeWorkflow() {
            val stub = environment.workflowClient.newWorkflowStub(
                EnrichmentWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
                    .setWorkflowId(EnrichmentWorkflow.workflowId(queueItemId))
                    .build()
            )
            stub.embed(queueItemId)
        }
    }

    // ------ WithExtraConsumer: Tests 3 and 4 ------

    /**
     * Tests run with two consumers: the real [EmbeddingConsumer] (Phase 1) plus a test-only
     * mock [ConsumerActivity] injected via [TestEnrichmentWorkflowImpl.extraConsumers].
     *
     * Validates that the workflow scaffold accepts [List]<[ConsumerActivity]> of size > 1.
     */
    @Nested
    inner class WithExtraConsumer {

        private lateinit var environment: TestWorkflowEnvironment
        private lateinit var delegate: ActivityDelegate
        private lateinit var testConsumer: ConsumerActivity

        @BeforeEach
        fun setUp() {
            delegate = mock()
            testConsumer = mock()
            val activitiesImpl = DelegatingActivitiesImpl(delegate)

            environment = newTestEnvironment()
            val worker: Worker = environment.newWorker(TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
            // Two consumers: EmbeddingConsumer (wired to the activities stub) + test-only consumer mock
            worker.registerWorkflowImplementationFactory(EnrichmentWorkflow::class.java) {
                TestEnrichmentWorkflowImpl(extraConsumers = listOf(testConsumer))
            }
            worker.registerActivitiesImplementations(activitiesImpl)
            environment.start()
        }

        @AfterEach
        fun tearDown() {
            if (::environment.isInitialized) environment.close()
        }

        /**
         * ENRICH-05 Contract 3: the workflow scaffold accepts [List]<[ConsumerActivity]> of size > 1.
         *
         * Injects a second test-only [ConsumerActivity] alongside the [EmbeddingConsumer]. Both must
         * run when analysis succeeds and embedding succeeds. Verifies that the fan-out iterates ALL
         * consumers, not just the first one.
         *
         * Covers: ENRICH-05 sub-contract (c) workflow-accepts-List<ConsumerActivity>.
         */
        @Test
        fun `workflow scaffold accepts List of ConsumerActivity and runs all consumers`() {
            whenever(delegate.analyzeSemantics(queueItemId)).thenReturn(fixtureContext)
            // embedAndStore succeeds — EmbeddingConsumer's activity completes normally

            val stub = environment.workflowClient.newWorkflowStub(
                EnrichmentWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
                    .setWorkflowId(EnrichmentWorkflow.workflowId(queueItemId))
                    .build()
            )

            assertDoesNotThrow { stub.embed(queueItemId) }

            // EmbeddingConsumer ran — verified via the Temporal activity invocation through DelegatingActivitiesImpl
            verify(delegate, times(1)).embedAndStore(eq(fixtureContext), eq(queueItemId))
            // Test-only consumer also ran with the same context and queueItemId
            verify(testConsumer, times(1)).run(eq(fixtureContext), eq(queueItemId))
        }

        /**
         * Defensive: a non-embedding consumer's terminal failure is also swallowed by runCatching,
         * and the sibling [EmbeddingConsumer] still runs.
         *
         * The test consumer is configured to throw; the workflow must complete successfully AND
         * [EmbeddingConsumer] (which delegates to embedAndStore) must still be invoked.
         * Consumer ordering: EmbeddingConsumer is first (Phase 1 production order), testConsumer
         * is second. EmbeddingConsumer runs before testConsumer's failure, proving independence.
         *
         * Covers: ENRICH-05 general sibling-independence semantics (not just embedding).
         */
        @Test
        fun `runCatching swallows non-embedding consumer failures and sibling consumers still run`() {
            whenever(delegate.analyzeSemantics(queueItemId)).thenReturn(fixtureContext)
            // testConsumer throws — simulating a future sibling consumer (e.g. Synthesis) failing terminally
            whenever(testConsumer.run(any(), any()))
                .thenThrow(RuntimeException("simulated test consumer failure"))

            val stub = environment.workflowClient.newWorkflowStub(
                EnrichmentWorkflow::class.java,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE)
                    .setWorkflowId(EnrichmentWorkflow.workflowId(queueItemId))
                    .build()
            )

            // Workflow must not propagate the test consumer's failure
            assertDoesNotThrow { stub.embed(queueItemId) }

            // EmbeddingConsumer (first in list) still executed — not blocked by second consumer's failure
            verify(delegate, times(1)).embedAndStore(eq(fixtureContext), eq(queueItemId))
            // The failing test consumer was invoked (it just failed, not skipped)
            verify(testConsumer, times(1)).run(eq(fixtureContext), eq(queueItemId))
        }
    }
}

// ------ Test-only helpers ------

/**
 * Non-annotated delegate interface for [DelegatingActivitiesImpl].
 *
 * Temporal's `registerActivitiesImplementations` rejects Mockito-generated dynamic proxy classes
 * when those proxies inherit `@ActivityMethod` annotations from the `@ActivityInterface` they mock.
 * Temporal allows `@ActivityMethod` annotations only on the original interface declaration, not on
 * proxy subclasses.
 *
 * By defining a plain interface ([ActivityDelegate]) without `@ActivityInterface` / `@ActivityMethod`,
 * Mockito mocks of [ActivityDelegate] are annotation-free and can be used freely in assertions.
 * [DelegatingActivitiesImpl] — a concrete class registered with Temporal — delegates to the mock.
 */
interface ActivityDelegate {
    fun analyzeSemantics(queueItemId: UUID): EnrichmentContext
    fun embedAndStore(context: EnrichmentContext, queueItemId: UUID)
    fun markQueueItemFailed(queueItemId: UUID, reason: String)
}

/**
 * Concrete [EnrichmentActivities] implementation that delegates to [ActivityDelegate].
 *
 * Registered directly with [TestWorkflowEnvironment]'s worker (Temporal accepts it because it
 * is a concrete class, not a Mockito proxy). All method calls forward to [delegate], which is a
 * Mockito mock of the annotation-free [ActivityDelegate] interface — allowing full `whenever`/`verify`
 * control from the test while satisfying Temporal's registration constraints.
 */
private class DelegatingActivitiesImpl(private val delegate: ActivityDelegate) : EnrichmentActivities {
    override fun analyzeSemantics(queueItemId: UUID): EnrichmentContext =
        delegate.analyzeSemantics(queueItemId)

    override fun embedAndStore(context: EnrichmentContext, queueItemId: UUID) =
        delegate.embedAndStore(context, queueItemId)

    override fun markQueueItemFailed(queueItemId: UUID, reason: String) =
        delegate.markQueueItemFailed(queueItemId, reason)
}

/**
 * Test-only workflow subclass that injects extra consumers and uses reduced activity timeouts
 * to prevent [TestWorkflowEnvironment] from blocking on long retry windows.
 *
 * **Why this class exists:**
 * - [TestWorkflowEnvironment] cannot inject constructor args into workflow instances directly;
 *   the factory pattern (`registerWorkflowImplementationFactory`) is the supported mechanism.
 * - Reduced timeouts (2s startToClose, 100ms→500ms backoff, 3 attempts) bound test execution
 *   to well under the 30s [Timeout] on the outer class while still exercising retry semantics.
 * - `buildConsumers` appends extra consumers AFTER the [EmbeddingConsumer] so the EmbeddingConsumer
 *   is always first in the fan-out list (matching Phase 1 production order).
 *
 * @param extraConsumers additional consumers to inject after [EmbeddingConsumer] in the fan-out list
 */
private class TestEnrichmentWorkflowImpl(
    private val extraConsumers: List<ConsumerActivity>,
) : EnrichmentWorkflowImpl() {

    override fun createActivitiesStub(): EnrichmentActivities =
        Workflow.newActivityStub(
            EnrichmentActivities::class.java,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(2))
                .setRetryOptions(
                    RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofMillis(100))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMillis(500))
                        .build()
                )
                .build()
        )

    /**
     * Returns extra **sibling** consumers only. The primary [EmbeddingConsumer] is built by
     * [EnrichmentWorkflowImpl.buildPrimaryConsumer] (the production default) and is the
     * queue-completion path — it is NOT a sibling and must not appear in this list.
     */
    override fun buildConsumers(stub: EnrichmentActivities): List<ConsumerActivity> = extraConsumers
}
