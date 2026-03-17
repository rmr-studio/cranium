---
Created: 2026-03-17
Domains:
  - "[[Identity Resolution]]"
tags:
  - component/active
  - layer/service
  - architecture/component
---

Part of [[Temporal Integration]]

# IdentityMatchWorkflow

## Purpose

Temporal workflow interface and implementation for the identity matching pipeline. Orchestrates three independently retryable activities in sequence with short-circuit on empty results.

## Interface: IdentityMatchWorkflow

- `@WorkflowMethod matchEntity(entityId: UUID, workspaceId: UUID, userId: UUID?): Int` — returns suggestion count

### Companion Object

- `workflowId(entityId: UUID): String` — returns `"identity-match-{entityId}"` for deduplication

## Implementation: IdentityMatchWorkflowImpl

- **NOT a Spring bean** — Temporal manages lifecycle
- **No-arg constructor** — Temporal requirement
- Uses `Workflow.getLogger()` for logging, `Workflow.newActivityStub()` for activity creation

### Activity Options

| Setting | Value |
|---------|-------|
| startToCloseTimeout | 30s |
| Max attempts | 3 |
| Initial interval | 1s |
| Max interval | 10s |
| Backoff coefficient | 2.0 |

### Pipeline Flow

1. **FindCandidates** — if empty, return 0 (short-circuit)
2. **Get trigger attributes**
3. **ScoreCandidates** — if empty, return 0 (short-circuit)
4. **PersistSuggestions** — return count

## Workflow ID Convention

`identity-match-{entityId}` ensures only one workflow per entity runs concurrently. `WorkflowExecutionAlreadyStarted` is handled gracefully by the processor.

## Gotchas

- **WorkflowImpl must remain deterministic** — no direct I/O, no Spring injection. All side effects go through activity stubs.
- Uses `Workflow.getLogger()`, not injected `KLogger`.

## Related

- [[IdentityMatchActivitiesImpl]]
- [[IdentityMatchQueueProcessorService]]
