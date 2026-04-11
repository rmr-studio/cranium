---
tags:
  - component/active
  - layer/service
  - architecture/component
  - domain/identity-resolution
Created: 2026-03-19
Domains:
  - "[[riven/docs/system-design/domains/Identity Resolution/Identity Resolution]]"
Sub-Domains:
  - "[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Matching Pipeline/Matching Pipeline]]"
---

# IdentityMatchQueueProcessorService

## Purpose

Processes individual IDENTITY_MATCH queue items in isolated transactions, claiming batches via `SKIP LOCKED` and dispatching each item as a Temporal workflow.

## Responsibilities

**Owns:**
- Claiming batches of queue items using `SKIP LOCKED` for concurrent-safe processing
- Processing each item in its own transaction (REQUIRES_NEW) for failure isolation
- Dispatching IdentityMatchWorkflow to Temporal via async workflow stub
- Retry/failure handling per item based on attempt count vs MAX_ATTEMPTS (3)
- Releasing items back to PENDING on retryable failures

**Does NOT own:**
- Scheduling or polling (IdentityMatchDispatcherService)
- Queue enqueue logic (IdentityMatchQueueService)
- Workflow execution (Temporal / IdentityMatchWorkflow)

## Dependencies

| Dependency | Type | Purpose |
|---|---|---|
| WorkflowExecutionQueueService | Injected | Status transitions (mark CLAIMED, DISPATCHED, FAILED, release to PENDING) |
| ExecutionQueueRepository | Injected | Batch claim query via SKIP LOCKED |
| WorkflowClient | Injected | Creates Temporal workflow stubs for async dispatch |
| KLogger | Injected | Structured logging |

## Consumed By

| Consumer | Method | Context |
|---|---|---|
| IdentityMatchDispatcherService | claimBatch, processItem | Scheduled polling loop — claims a batch then iterates items |

## Public Interface

### claimBatch

```kotlin
@Transactional
fun claimBatch(size: Int): List<ExecutionQueueEntity>
```

Claims up to `size` PENDING IDENTITY_MATCH items from the execution queue using `SKIP LOCKED`. Returns the claimed items with status transitioned to CLAIMED.

**Parameters:**
- `size` — maximum number of items to claim in this batch

### processItem

```kotlin
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun processItem(item: ExecutionQueueEntity)
```

Processes a single claimed queue item in its own transaction. Creates a Temporal workflow stub on the `identity.match` task queue, starts IdentityMatchWorkflow asynchronously, and marks the item as DISPATCHED on success.

**Parameters:**
- `item` — the claimed execution queue entity to process

## Key Logic

### REQUIRES_NEW Transaction Isolation

Each `processItem` call runs in its own transaction (`Propagation.REQUIRES_NEW`). This ensures:
- Row locks acquired during claim are released per item, not held for the entire batch
- A failure in one item does not roll back the processing of other items in the batch
- The dispatcher can continue iterating after a single item failure

### Processing Flow Per Item

1. Mark item as CLAIMED (if not already via batch claim)
2. Create a Temporal workflow stub targeting the `identity.match` task queue
3. Start IdentityMatchWorkflow asynchronously via the stub
4. Mark item as DISPATCHED on success

### WorkflowExecutionAlreadyStarted Handling

If Temporal reports that the workflow is already started (duplicate dispatch), the item is released back to PENDING for retry rather than being dropped. This handles race conditions where the same item could be dispatched twice.

### Retry and Failure Logic

| Condition | Behaviour |
|---|---|
| Error with attempts < MAX_ATTEMPTS (3) | Release item back to PENDING for retry |
| Error with attempts >= MAX_ATTEMPTS (3) | Mark item as FAILED (terminal state) |
| WorkflowExecutionAlreadyStarted | Release to PENDING (not counted as failure) |
| Success | Mark as DISPATCHED |

## Data Access

| Table | Access Method | Query Type |
|---|---|---|
| execution_queue | ExecutionQueueRepository | SKIP LOCKED batch claim query |

## Gotchas

- **REQUIRES_NEW is critical** — without it, all items in a batch share one transaction. A single failure would roll back the entire batch, and row locks would be held for the full iteration duration.
- **WorkflowExecutionAlreadyStarted is not an error** — it is a retryable condition. The item is released to PENDING, not marked FAILED.
- **MAX_ATTEMPTS = 3** — after 3 failed attempts, an item is permanently marked FAILED. There is no dead-letter queue or manual retry mechanism beyond this.
- **Cross-domain dependency** — uses WorkflowExecutionQueueService from the Workflows domain for all status transitions. The processor does not write queue status directly.

## Error Handling

| Scenario | Behaviour |
|---|---|
| WorkflowExecutionAlreadyStarted | Release to PENDING for retry |
| Any other exception (attempts < 3) | Release to PENDING, increment attempt count |
| Any other exception (attempts >= 3) | Mark FAILED (terminal) |
| Batch claim finds no items | Returns empty list, no side effects |

## Flows Involved

- [[riven/docs/system-design/domains/Identity Resolution/Flow - Identity Match Pipeline]]

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Identity Resolution/Matching Pipeline/Matching Pipeline]]
- [[IdentityMatchDispatcherService]]
- [[IdentityMatchQueueService]]
