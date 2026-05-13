---
tags:
  - adr/superseded
  - architecture/decision
Created: 2026-03-16
Updated: 2026-05-13
Superseded By: "[[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]]"
---

# ADR-008: Temporal for Integration Sync Orchestration

> **Partially superseded by the 2026-05 architecture pivot.** (Note: there are two ADRs numbered 008 in this folder — this one and "ADR-008 Integration-Based Entity Types as Readonly Materialized Views"; renumbering is deferred to avoid breaking links.) **Temporal stays** — it's the orchestration backbone for the new GitHub-scan + LLM-synthesis pipeline and the page-resolution workflow, reusing the same isolated-task-queue / ShedLock + `SKIP LOCKED` dispatcher / workflow-ID-dedup patterns this ADR established. **What's deleted is the integration-sync workflow itself** (the 3-pass `IntegrationSyncWorkflow`, the `integration.sync` task queue, the Nango record-fetch/persist/relationship-resolve/health activities) — that's Phase 1c. The replacement: a deterministic source-parse stage + a batched, coalesced LLM synthesis Temporal workflow over `new_source_events_queue`. See [[architecture-pivot]] §Reuse / Rework / Replace / Delete and [[ADR-014 Deterministic Source-Entity Creation plus Batched LLM Synthesis]].

---

## Context

The integration sync pipeline needs to fetch records from Nango's API (paginated, potentially thousands of records), map them to entities, persist with deduplication, resolve relationships, and update connection health. This involves external API calls, database writes, and multi-step processing that can fail at any point.

The system must handle Nango's at-least-once webhook delivery, retry transient failures, and provide visibility into sync execution. Initial syncs for large datasets can involve thousands of records across many pages, making the process inherently long-running. A failure partway through a sync (e.g., after processing 50 of 200 pages) must not require restarting from scratch.

The existing Workflows domain already uses Temporal for DAG-based workflow execution, so the infrastructure (Temporal Server, worker registration, SDK dependencies) is already deployed and operational.

---

## Decision

Use Temporal for all sync workflow orchestration with a dedicated `integration.sync` task queue. Each sync execution is a Temporal workflow with a deterministic workflow ID derived from the connection, model, and modification timestamp. Record fetching, entity persistence, relationship resolution, and connection health updates are implemented as separate Temporal activities within the workflow.

---

## Rationale

- **Durable execution** — workflow state survives process restarts; long-running backfills (potentially hours for initial syncs with thousands of records) will not be lost
- **Built-in retry with configurable policies** (3 attempts, 30s initial interval, 2x backoff, 5min max interval) — replaces the need for custom retry infrastructure
- **Deterministic workflow IDs** (`integration-sync-{connectionId}-{syncModel}-{modifiedAfter}`) provide native webhook deduplication — Temporal rejects duplicate workflow starts, handling Nango's at-least-once delivery guarantee without application-level dedup logic
- **Activity heartbeating** for paginated record fetching — prevents timeout on large datasets while allowing Temporal to detect stuck workers
- **Workflow visibility** — Temporal UI provides execution history, input/output inspection, and retry controls without building custom observability tooling
- **Dedicated task queue** (`integration.sync`) isolates sync load from user-triggered workflow execution on the main queue, preventing sync backpressure from affecting user-facing operations
- **No new infrastructure dependency** — the project already has Temporal Server deployed and configured for the Workflows domain

---

## Alternatives Considered

### Option 1: Spring @Async with @Retryable

Run sync processing in Spring-managed async threads with method-level retry annotations.

- **Pros:** No additional infrastructure. Simple annotation-based configuration. Familiar Spring pattern.
- **Cons:** No durability — in-flight syncs are lost on process restart. No execution visibility beyond application logs. Retry is limited to method-level granularity, not step-level. No native deduplication for at-least-once webhook delivery. Thread pool exhaustion under high sync volume.
- **Why rejected:** Lacks durability and visibility, which are critical for long-running sync operations that process thousands of records.

### Option 2: Message Queue (RabbitMQ/Kafka)

Introduce a message queue for sync job dispatch with consumer-based processing.

- **Pros:** Proven async processing pattern. Decouples webhook receipt from processing. Supports backpressure.
- **Cons:** New infrastructure dependency to deploy and maintain. Manual state management for multi-step workflows. No built-in orchestration — must implement saga or state machine pattern manually. Deduplication requires additional application logic or message dedup features.
- **Why rejected:** Introduces a new infrastructure dependency when Temporal already provides superior workflow orchestration capabilities that are already deployed.

### Option 3: Synchronous Processing in Webhook Handler

Process sync records directly in the webhook HTTP handler, returning a response only after completion.

- **Pros:** Simplest implementation. No async complexity. Easy to reason about.
- **Cons:** Blocks the webhook response, risking Nango webhook timeout. No retry on partial failure. Single request must complete all processing, which is infeasible for large initial syncs. Nango may retry the webhook if the response is slow, causing duplicate processing.
- **Why rejected:** Fundamentally incompatible with the scale and reliability requirements of the sync pipeline.

### Option 4: Spring Batch

Use Spring Batch's job/step/chunk processing model for sync execution.

- **Pros:** Built-in chunk processing, skip/retry policies, job repository for execution tracking.
- **Cons:** Designed for batch file processing and scheduled jobs, not API-dcranium event workflows. Poor fit for webhook-triggered async processing. Job parameterization is cumbersome for dynamic sync inputs. No native support for external API pagination as a data source.
- **Why rejected:** Architectural mismatch — Spring Batch is optimized for scheduled ETL-style batch jobs, not event-dcranium integration sync workflows.

---

## Consequences

### Positive

- Sync workflows survive process restarts with full state recovery
- Nango's at-least-once webhook delivery is handled via deterministic workflow IDs without application-level dedup
- Failed activities are retried automatically with configurable backoff, reducing manual intervention
- Temporal UI provides execution history, input/output inspection, and manual retry controls for operational support
- Sync processing is isolated on a dedicated task queue, preventing interference with user-triggered workflows

### Negative

- Temporal worker registration boilerplate — requires a configuration class to register the workflow and activities on the dedicated task queue
- Activity and workflow interface ceremony — each logical step requires an interface, implementation, and registration
- Workflow code must remain deterministic — no direct I/O, random values, or system clock access in workflow methods (only in activities)

### Neutral

- Adds approximately 4 new files: workflow interface, workflow implementation, activities interface, and activities implementation, plus a configuration class for worker registration
- Sync execution metrics and logs are in Temporal's execution history rather than application logs — operators must check both systems during debugging

---

## Implementation Notes

- Task queue constant: `INTEGRATION_SYNC_QUEUE = "integration.sync"` defined in a shared constants location
- Configuration class: `IntegrationSyncTemporalConfiguration` registers the workflow implementation and activities bean on the `integration.sync` task queue
- Retry policy: `RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(30)).setBackoffCoefficient(2.0).setMaximumInterval(Duration.ofMinutes(5)).build()`
- Workflow ID format: `integration-sync-{connectionId}-{syncModel}-{modifiedAfter}` — enables idempotent webhook processing by rejecting duplicate starts
- Activities are Spring beans with constructor-injected dependencies, registered on the worker via the configuration class
- Heartbeating is used during paginated record fetching to report progress and prevent activity timeout on large datasets

---

## Related

- [[cranium/docs/system-design/flows/Integration Data Sync Pipeline]] — Feature design for the sync pipeline that this decision supports
- [[cranium/docs/system-design/feature-design/_Sub-Domain Plans/Entity Integration Sync]] — Sub-domain plan for entity sync processing
- [[cranium/docs/system-design/domains/Workflows/Workflows]] — Existing domain that established Temporal as the workflow orchestration platform
- [[2. Areas/2.1 Startup & Content/Cranium/2. System Design/decisions/ADR-001 Nango as Integration Infrastructure]] — Decision to use Nango, whose webhook delivery model motivates Temporal's dedup capability
