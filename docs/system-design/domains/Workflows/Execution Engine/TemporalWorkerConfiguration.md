---
tags:
  - component/active
  - layer/configuration
  - architecture/component
Created: 2026-02-09
Updated: 2026-03-17
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/Execution Engine]]

# TemporalWorkerConfiguration

---

## Purpose

Registers Temporal workers for multiple task queues, configuring which workflows and activities run on each queue. Provides graceful shutdown via `@PreDestroy`.

---

## Condition

`@ConditionalOnProperty(name = "riven.workflow.engine.enabled", havingValue = "true", matchIfMissing = true)`

Workers are registered by default unless explicitly disabled.

---

## Task Queues

| Queue Name | Constant | Purpose | Workflows | Activities |
|------------|----------|---------|-----------|------------|
| workflows.default | WORKFLOW_QUEUE | Default workflow execution | WorkflowOrchestrationService | WorkflowCoordinationService, WorkflowCompletionActivityImpl |
| activities.external-api | EXTERNAL_API_QUEUE | External API activities (isolated) | — | (external API activities) |
| identity.match | IDENTITY_MATCH_QUEUE | Identity matching pipeline | IdentityMatchWorkflowImpl | IdentityMatchActivitiesImpl |
| enrichment.embed | ENRICHMENT_EMBED_QUEUE | Entity embedding enrichment pipeline. Isolated from default + identity queues so embedding-API latency cannot block other workloads. | [[EnrichmentWorkflow]] (`EnrichmentWorkflowImpl`) | [[EnrichmentActivitiesImpl]] |

---

## Bean

### `workerFactory(): WorkerFactory`

Creates a `WorkerFactory` from `WorkflowClient`, registers workers for each task queue, and calls `factory.start()`.

---

## Queue Isolation

Identity matching runs on a dedicated queue (`identity.match`) to prevent matching workload from blocking workflow executions on the default queue. Each queue gets its own Temporal worker.

The embedding pipeline runs on its own `enrichment.embed` queue for the same reason — embedding API calls (OpenAI, Ollama) can take seconds and would otherwise stall identity matching and workflow execution. `EnrichmentWorkflowImpl` configures a 60s `startToCloseTimeout` per activity to accommodate that latency.

---

## Workspace Queues

`workspaceQueue(workspaceId): String` generates `"workflow.workspace.{uuid}"` format — not used in V1 (all workspaces share default queue).

---

## Injected Dependencies

```kotlin
@Configuration
class TemporalWorkerConfiguration(
    private val workflowClient: WorkflowClient,
    private val workflowCoordinationService: WorkflowCoordinationService,
    private val workflowCompletionActivityImpl: WorkflowCompletionActivityImpl,
    private val identityMatchActivitiesImpl: IdentityMatchActivitiesImpl,
    private val enrichmentActivities: EnrichmentActivitiesImpl,
    private val logger: KLogger
)
```

---

## Gotchas & Edge Cases

> [!warning] Workflow Types vs Activity Instances
> WorkflowOrchestrationService and IdentityMatchWorkflowImpl are registered as workflow TYPES (not instances) — Temporal creates instances per workflow execution. Activity implementations are registered as instances (Spring beans).

> [!warning] @PreDestroy Shutdown
> `@PreDestroy` calls `factory.shutdown()` for graceful worker shutdown. This ensures in-flight activities complete before the pod terminates.

> [!warning] matchIfMissing = true
> The `@ConditionalOnProperty` has `matchIfMissing = true`, meaning workers are registered by default unless `riven.workflow.engine.enabled` is explicitly set to `false`. This is important for test profiles that may not define this property.

> [!warning] Enrichment Queue Isolation
> `EnrichmentWorkflowImpl` uses a 60s `startToCloseTimeout` per activity to accommodate embedding API latency (OpenAI / Ollama). The dedicated `enrichment.embed` queue exists specifically so that latency cannot starve `workflows.default` or `identity.match`. Do not register enrichment workflows or activities on any other queue.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/Execution Engine]] — Parent subdomain
- [[riven/docs/system-design/domains/Identity Resolution/Temporal Integration/IdentityMatchWorkflow]] — Identity match workflow type registered on identity.match queue
- [[riven/docs/system-design/domains/Identity Resolution/Temporal Integration/IdentityMatchActivitiesImpl]] — Identity match activities registered on identity.match queue
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowOrchestrationService]] — Default workflow type registered on workflows.default queue
- [[EnrichmentWorkflow]] — Enrichment workflow type registered on enrichment.embed queue
- [[EnrichmentActivitiesImpl]] — Enrichment activities registered on enrichment.embed queue
