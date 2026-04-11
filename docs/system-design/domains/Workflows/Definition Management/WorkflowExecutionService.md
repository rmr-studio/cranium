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
# WorkflowExecutionService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/Definition Management]]

## Purpose

Service for managing workflow execution lifecycle: queuing execution requests, querying execution status and history, and retrieving execution summaries with node details.

---

## Responsibilities

- Queue workflow execution requests via [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionQueueService]]
- Validate workspace access before queueing
- Query execution records by ID, workflow definition, or workspace
- Retrieve execution summaries with node execution details via JOIN query
- Log activity for audit trail

---

## Dependencies

- [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionQueueService]] — Enqueues execution requests
- `WorkflowExecutionRepository` — Persistence for execution records
- `ActivityService` — Audit logging
- `AuthTokenService` — JWT user extraction

## Used By

- Workflow API controllers — REST endpoints for starting and querying executions
- [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionDispatcherService]] — Dispatches queued executions to Temporal (async)

---

## Key Logic

**Execution flow:**

1. **Start:** Queue execution request (this service)
2. **Dispatch:** [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionDispatcherService]] claims queue item and starts Temporal workflow (async)
3. **Execute:** Temporal workflow orchestrates DAG
4. **Complete:** [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowCompletionActivityImpl]] records final status
5. **Query:** This service retrieves execution details

**Queue-based async execution:**

`startExecution()` returns immediately with queue ID. Actual Temporal dispatch happens asynchronously via [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionDispatcherService]], which checks tier-based capacity limits.

**Execution summary with node details:**

`getExecutionSummary()` uses single JOIN query (`findExecutionWithNodesByExecutionId`) to fetch:
- Execution record with final status and error
- All node execution records with status, output, and error
- Workflow node definitions (for display)

Returns `WorkflowExecutionSummaryResponse` with execution and list of node executions.

---

## Public Methods

### `startExecution(request): ExecutionQueueRequest`

Queues workflow execution request. Validates workspace access. Returns queue item model with status PENDING. Logs activity.

### `getExecutionById(id, workspaceId): WorkflowExecutionRecord`

Retrieves single execution record by ID. Validates workspace access. Throws `NotFoundException` if not found or workspace mismatch.

### `listExecutionsForWorkflow(workflowDefinitionId, workspaceId): List<WorkflowExecutionRecord>`

Lists all executions for a workflow definition, ordered by most recent first.

### `getWorkspaceExecutionRecords(workspaceId): List<WorkflowExecutionRecord>`

Lists all executions for a workspace across all workflow definitions, ordered by most recent first.

### `getExecutionSummary(executionId, workspaceId): WorkflowExecutionSummaryResponse`

Retrieves execution with all node execution details via single JOIN query. Returns execution record and list of node executions with workflow node definitions.

---

## Gotchas

- **Async execution:** `startExecution()` doesn't start Temporal workflow directly. It queues the request. Actual dispatch happens async via [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionDispatcherService]].
- **Error details in JSONB:** Execution errors stored as JSONB (`WorkflowExecutionError`), node errors stored as JSONB (`NodeExecutionError`). Auto-serialized to JSON in API responses.
- **JOIN query for summary:** `getExecutionSummary()` uses custom repository query with JOINs to fetch all related data in one query (execution, node executions, workflow nodes). Efficient for detail views.

---

## Related

- [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionQueueService]] — Queue management
- [[riven/docs/system-design/domains/Workflows/Queue Management/WorkflowExecutionDispatcherService]] — Async Temporal dispatch
- [[riven/docs/system-design/domains/Workflows/Execution Engine/WorkflowCompletionActivityImpl]] — Records completion
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Definition Management/Definition Management]] — Parent subdomain
