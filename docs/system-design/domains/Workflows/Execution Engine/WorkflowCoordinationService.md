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
# WorkflowCoordinationService

Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/Execution Engine]]

## Purpose

Temporal activity bridge between the deterministic workflow orchestrator and Spring-managed services, coordinating individual workflow node execution by delegating to [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] for DAG orchestration.

---

## Responsibilities

- Fetch workflow nodes and edges from database (allowed in activities, not workflows)
- Create `WorkflowDataStore` with execution metadata and initial state
- Provide `NodeServiceProvider` callback for on-demand Spring service access during node execution
- Execute batch of ready nodes via callback passed to graph coordinator
- Resolve node input templates against `WorkflowDataStore` via [[riven/docs/system-design/domains/Workflows/State Management/WorkflowNodeInputResolverService]]
- Execute nodes polymorphically via `node.execute(store, inputs, services)`
- Persist node execution records to `workflow_execution_node` table
- Classify and throw `ApplicationFailure` for Temporal retry handling
- Update execution records with success/failure status and structured error details

---

## Dependencies

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] — DAG orchestration and batch scheduling
- [[riven/docs/system-design/domains/Workflows/State Management/WorkflowNodeInputResolverService]] — Template resolution
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowErrorClassifier]] — Exception classification for retry behavior
- `WorkflowExecutionNodeRepository` — Persistence for node execution state
- `NodeServiceProvider` — On-demand service injection for nodes

## Used By

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowOrchestrationService]] — Invokes as Temporal activity for node coordination

---

## Key Logic

**Execution flow:**

1. Fetch nodes and edges from database (activity context allows DB access)
2. Create `WorkflowDataStore` with metadata from Temporal activity context
3. Define `nodeExecutor` callback that resolves inputs and executes nodes
4. Delegate to `WorkflowGraphCoordinationService.executeWorkflow()` with callback
5. Coordinator calls back with ready node batches
6. For each node: resolve inputs, execute polymorphically, capture output, persist state

**Error classification:**

Uses [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowErrorClassifier]] to categorize exceptions:
- HTTP 4xx → `HTTP_CLIENT_ERROR` (non-retryable)
- HTTP 5xx → `HTTP_SERVER_ERROR` (retryable)
- CONTROL_FLOW nodes → `CONTROL_FLOW_ERROR` (deterministic, non-retryable)
- Validation errors → `VALIDATION_ERROR` (non-retryable)
- All others → `EXECUTION_ERROR` (retryable, transient assumption)

Throws `ApplicationFailure` with error type for Temporal's retry policy matching.

**Input resolution dispatch:**

The coordinator extracts config properties for template resolution based on node config type. Supported action configs:
- `WorkflowCreateEntityActionConfig` → entity type ID and payload templates
- `WorkflowUpdateEntityActionConfig` → entity ID, type ID, and payload templates
- `WorkflowDeleteEntityActionConfig` → entity ID template
- `WorkflowQueryEntityActionConfig` → query config (filter templates resolved separately within execute())
- `WorkflowBulkUpdateEntityActionConfig` → query config and payload templates
- `WorkflowHttpRequestActionConfig` → URL, headers, and body templates

---

## Public Methods

### `executeWorkflowWithCoordinator(workflowDefinitionId, nodeIds, workspaceId): WorkflowDataStore`

Executes workflow DAG by coordinating with graph coordinator. Fetches nodes/edges, creates datastore, defines executor callback, delegates orchestration. Returns final datastore with all step outputs.

---

## Gotchas

- **Spring bean in Temporal activity:** Temporal's Spring Boot starter auto-registers this as an activity. The interface `WorkflowCoordination` has the `@ActivityInterface` annotation, not this implementation.
- **Sequential batch execution:** Nodes in the same batch currently execute sequentially (not truly parallel). TODO at line 115 indicates future parallel execution via Temporal child workflows.
- **Single execution record per node:** Creates `WorkflowExecutionNodeEntity` BEFORE try block and reuses it in both success and error paths to prevent duplicate records during retries.
- **Polymorphic dispatch:** No type switching on node type. Each `WorkflowNode` implementation provides its own `execute()` method, enabling extensibility.

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowOrchestrationService]] — Parent workflow
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowGraphCoordinationService]] — DAG orchestration
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Execution Engine/WorkflowErrorClassifier]] — Error classification utility
