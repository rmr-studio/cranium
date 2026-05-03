package riven.core.configuration.workflow

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.WorkerFactory
import jakarta.annotation.PreDestroy
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import riven.core.service.integration.sync.IntegrationSyncActivities
import riven.core.service.integration.sync.IntegrationSyncWorkflow
import riven.core.service.integration.sync.IntegrationSyncWorkflowImpl
import riven.core.service.workflow.engine.WorkflowOrchestration
import riven.core.service.workflow.engine.WorkflowOrchestrationService
import riven.core.service.workflow.engine.completion.WorkflowCompletionActivityImpl
import riven.core.service.workflow.engine.coordinator.WorkflowCoordinationService
import riven.core.service.workflow.enrichment.EnrichmentActivitiesImpl
import riven.core.service.workflow.enrichment.EnrichmentWorkflow
import riven.core.service.workflow.enrichment.EnrichmentWorkflowImpl
import riven.core.service.workflow.identity.IdentityMatchActivitiesImpl
import riven.core.service.workflow.identity.IdentityMatchWorkflow
import riven.core.service.workflow.identity.IdentityMatchWorkflowImpl
import java.util.*

/**
 * Spring configuration for Temporal workers.
 *
 * This configuration:
 * 1. Creates a WorkflowClient from existing WorkflowServiceStubs bean
 * 2. Creates a WorkerFactory
 * 3. Creates workers for multiple task queues (default, external-api, per-workspace)
 * 4. Registers workflow implementations (WorkflowOrchestrationServiceImpl)
 * 5. Registers activity implementations (WorkflowCoordinationService Spring bean)
 * 6. Starts the worker factory
 * 7. Provides graceful shutdown via @PreDestroy
 *
 * Queue naming convention:
 * - workflows.default: System/internal workflows (V1: all workspace workflows routed here)
 * - activities.external-api: External API activities (isolation for rate-limited APIs)
 * - workflow.workspace.{uuid}: Per-workspace queues (future: tenant isolation)
 *
 * V1 Approach: All workspaces use WORKFLOWS_DEFAULT_QUEUE for simplicity.
 * Future: Per-workspace queue registration when workspace capacity/isolation needed.
 *
 * @property workflowServiceStubs Existing bean from TemporalEngineConfiguration
 * @property activities Autowired Spring bean with injected dependencies
 */
@Configuration
@ConditionalOnProperty(name = ["riven.workflow.engine.enabled"], havingValue = "true", matchIfMissing = true)
class TemporalWorkerConfiguration(
    private val workflowServiceStubs: WorkflowServiceStubs,
    private val coordinationActivity: WorkflowCoordinationService,
    private val completionActivity: WorkflowCompletionActivityImpl,
    private val retryProperties: WorkflowRetryConfigurationProperties,
    private val identityMatchActivities: IdentityMatchActivitiesImpl,
    private val logger: KLogger,
    private val integrationSyncActivities: IntegrationSyncActivities,
    private val enrichmentActivities: EnrichmentActivitiesImpl,
) {

    companion object {
        /**
         * Default queue for system/internal workflows.
         *
         * V1: All workspace workflow executions are routed through this queue.
         * Future: Per-workspace queues for tenant isolation.
         */
        const val WORKFLOWS_DEFAULT_QUEUE = "workflows.default"

        /**
         * Queue for external API activities (isolation).
         *
         * Separates external API calls from internal workflows to:
         * - Prevent rate-limited APIs from blocking other work
         * - Allow independent scaling of API activity workers
         */
        const val ACTIVITIES_EXTERNAL_API_QUEUE = "activities.external-api"

        /**
         * Dedicated task queue for identity matching workflows and activities.
         *
         * Isolated from the default workflow queue to prevent identity matching workload
         * from affecting existing workflow engine execution.
         */
        const val IDENTITY_MATCH_QUEUE = "identity.match"

        /**
         * Queue for integration sync workflows.
         *
         * Dedicated queue for Nango sync webhook-triggered workflows.
         * Isolation ensures that long-running sync activities do not block
         * the default workflow queue.
         */
        const val INTEGRATION_SYNC_QUEUE = "integration.sync"

        /**
         * Dedicated task queue for entity embedding enrichment workflows and activities.
         *
         * Isolated from other queues so that embedding API latency does not affect
         * workflow or identity matching workloads.
         */
        const val ENRICHMENT_EMBED_QUEUE = "enrichment.embed"

        /**
         * Generate task queue name for a workspace.
         *
         * Currently not used (V1 uses default queue), but provides
         * naming convention for future per-workspace queue support.
         *
         * @param workspaceId Workspace UUID
         * @return Queue name in format "workflow.workspace.{uuid}"
         */
        fun workspaceQueue(workspaceId: UUID): String = "workflow.workspace.$workspaceId"
    }

    private lateinit var workerFactoryInstance: WorkerFactory

    /**
     * Creates and starts the WorkerFactory with registered workflows and activities.
     *
     * This bean:
     * - Creates WorkflowClient from service stubs
     * - Creates WorkerFactory
     * - Registers WorkflowExecutionWorkflowImpl (no-arg constructor required by Temporal)
     * - Registers WorkflowNodeActivitiesImpl Spring bean (with dependencies)
     * - Starts worker factory (non-blocking - workers poll in background)
     *
     * @return Started WorkerFactory instance
     */
    @Bean
    fun workerFactory(): WorkerFactory {
        logger.info { "Initializing Temporal WorkerFactory" }

        // Create WorkflowClient from service stubs
        val client = WorkflowClient.newInstance(workflowServiceStubs)

        // Create WorkerFactory
        val factory = WorkerFactory.newInstance(client)

        // Create worker for default queue (V1: all workflows use this queue)
        val worker = factory.newWorker(WORKFLOWS_DEFAULT_QUEUE)

        // Register workflow implementations via factory pattern
        // This allows injecting configuration from Spring into workflow instances
        // NOT a Spring bean - Temporal manages lifecycle, but config comes from Spring
        worker.registerWorkflowImplementationFactory(
            WorkflowOrchestration::class.java
        ) {
            WorkflowOrchestrationService(retryProperties.default)
        }
        logger.info { "Registered workflow: WorkflowOrchestrationService with retry config: ${retryProperties.default}" }

        // Register activity implementations
        // Note: Passing Spring bean instances enables dependency injection
        worker.registerActivitiesImplementations(
            coordinationActivity,
            completionActivity
        )
        logger.info { "Registered activities: WorkflowCoordination, WorkflowCompletionActivity" }

        // Create worker for identity matching pipeline on dedicated queue
        val identityWorker = factory.newWorker(IDENTITY_MATCH_QUEUE)
        identityWorker.registerWorkflowImplementationFactory(IdentityMatchWorkflow::class.java) {
            IdentityMatchWorkflowImpl()
        }
        identityWorker.registerActivitiesImplementations(identityMatchActivities)
        logger.info { "Registered identity match workflow and activities on task queue: $IDENTITY_MATCH_QUEUE" }

        // Create worker for integration sync queue
        val syncWorker = factory.newWorker(INTEGRATION_SYNC_QUEUE)
        syncWorker.registerWorkflowImplementationFactory(IntegrationSyncWorkflow::class.java) {
            IntegrationSyncWorkflowImpl(retryProperties.integrationSync)
        }
        logger.info { "Registered workflow: IntegrationSyncWorkflowImpl with retry config: ${retryProperties.integrationSync}" }
        syncWorker.registerActivitiesImplementations(integrationSyncActivities)
        logger.info { "Registered activities: IntegrationSyncActivities" }

        // Create worker for enrichment embedding pipeline on dedicated queue
        val enrichmentWorker = factory.newWorker(ENRICHMENT_EMBED_QUEUE)
        enrichmentWorker.registerWorkflowImplementationFactory(EnrichmentWorkflow::class.java) {
            EnrichmentWorkflowImpl()
        }
        enrichmentWorker.registerActivitiesImplementations(enrichmentActivities)
        logger.info { "Registered enrichment workflow and activities on task queue: $ENRICHMENT_EMBED_QUEUE" }

        // Start workers (non-blocking - workers poll Temporal Service in background)
        factory.start()
        logger.info { "Temporal WorkerFactory started, listening on task queues: $WORKFLOWS_DEFAULT_QUEUE, $IDENTITY_MATCH_QUEUE, $INTEGRATION_SYNC_QUEUE, $ENRICHMENT_EMBED_QUEUE" }

        // Store instance for shutdown
        workerFactoryInstance = factory

        return factory
    }

    /**
     * Gracefully shutdown workers when application stops.
     *
     * This method is called by Spring during application shutdown.
     * It ensures:
     * - In-flight workflows/activities complete gracefully
     * - No new work is accepted
     * - Resources are released properly
     */
    @PreDestroy
    fun shutdown() {
        logger.info { "Shutting down Temporal WorkerFactory" }
        if (::workerFactoryInstance.isInitialized) {
            workerFactoryInstance.shutdown()
            logger.info { "Temporal WorkerFactory shutdown complete" }
        }
    }
}