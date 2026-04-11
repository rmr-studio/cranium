---
tags:
  - layer/service
  - component/active
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
# WorkflowCompletionActivityImpl

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/Execution Engine]]

## Purpose

Temporal activity for recording final workflow execution status, updating both the execution record and queue item in a single transaction after DAG completion.

---

## Responsibilities

- Update `WorkflowExecutionEntity` with final status (COMPLETED or FAILED)
- Calculate execution duration from start time to completion
- Store structured error details if workflow failed
- Update queue item status: delete on COMPLETED, mark FAILED with error message
- Transaction-safe completion handling

---

## Dependencies

- `WorkflowExecutionRepository` — Persistence for execution records
- `ExecutionQueueRepository` — Queue item updates

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowOrchestrationService]] — Invokes after DAG execution completes

---

## Key Logic

**Completion lifecycle:**

Dispatcher owns:
- PENDING → CLAIMED → DISPATCHED (via ShedLock for bulk claiming)

This service owns:
- DISPATCHED → COMPLETED/FAILED (targets specific execution_id, no locking needed)

**Queue item cleanup strategy:**

- **COMPLETED:** Delete queue item to keep table small and prevent unbounded growth
- **FAILED:** Keep queue item with status=FAILED and error message for debugging/retry

**No concurrency control needed:**

- Targets records by unique `execution_id` (no concurrent updates possible)
- Each execution_id is unique to a single Temporal workflow instance
- Runs in Temporal worker thread pool (not scheduled, no ShedLock or SKIP LOCKED)

---

## Public Methods

### `recordCompletion(executionId, status, error?)`

Records final workflow status. Updates execution record with status and duration. Deletes queue item (COMPLETED) or marks failed (FAILED). Handles missing records gracefully with warnings.

---

## Gotchas

- **Missing record handling:** If execution or queue item not found, logs warning but doesn't fail. The workflow completed regardless, so completion service shouldn't crash.
- **Error structure conversion:** Converts `WorkflowExecutionError` domain model to JSONB map for PostgreSQL storage via `mapErrorToJson()`.
- **Independent lifecycle:** Operates independently of [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionDispatcherService]]. Dispatcher claims work, this service records completion.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowOrchestrationService]] — Caller
- [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionDispatcherService]] — Owns queue claiming
- [[riven/docs/system-design/domains/Workflows/Queue Management/Queue Management]] — Queue lifecycle
