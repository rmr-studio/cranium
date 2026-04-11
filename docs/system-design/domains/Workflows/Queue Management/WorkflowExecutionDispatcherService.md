---
tags:
  - component/active
  - layer/service
  - architecture/component
Created: 2026-02-08
Updated: 2026-02-08
Domains:
  - "[[riven/docs/system-design/domains/Workflows/Workflows]]"
---
Part of [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Queue Management/Queue Management]]

# WorkflowExecutionDispatcherService

---

## Purpose

Scheduled service that polls the execution queue and dispatches pending workflow executions to Temporal. Uses ShedLock for distributed locking to ensure only one instance processes the queue at a time across a multi-instance deployment.

---

## Responsibilities

**This component owns:**
- Scheduled polling of the execution queue (every 5 seconds)
- Distributed locking to prevent concurrent processing across instances
- Orchestrating batch processing (claim batch, delegate to processor)
- Recovering stale queue items stuck in CLAIMED state
- Logging queue processing activity

**Explicitly NOT responsible for:**
- Processing individual queue items (delegated to WorkflowExecutionQueueProcessorService)
- Checking workspace capacity (handled by processor)
- Starting Temporal workflows (handled by processor)
- Managing queue item state transitions (handled by WorkflowExecutionQueueService)

---

## Dependencies

### Internal Dependencies

|Component|Purpose|Coupling|
|---|---|---|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Queue Management/WorkflowExecutionQueueProcessorService]]|Claims batches and processes individual items|High|
|[[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Queue Management/WorkflowExecutionQueueService]]|Recovers stale items stuck in CLAIMED state|Medium|

### External Dependencies

|Service/Library|Purpose|Failure Impact|
|---|---|---|
|ShedLock|Distributed locking across instances|Multiple instances may process concurrently (duplicate work)|
|Spring Scheduler|Fixed delay scheduling (@Scheduled)|Queue processing stops|
|PostgreSQL (via ShedLock)|Lock persistence|Locking fails, processing may overlap|

### Injected Dependencies

```kotlin
@Service
@ConditionalOnProperty(
    name = ["riven.workflow.engine.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class WorkflowExecutionDispatcherService(
    private val workflowExecutionQueueService: WorkflowExecutionQueueService,
    private val workflowExecutionQueueProcessorService: WorkflowExecutionQueueProcessorService,
    private val logger: KLogger
)
```

---

## Public Interface

### Key Methods

#### `processQueue()`

- **Purpose:** Main scheduled method that processes a batch of pending queue items
- **When to use:** Called automatically by Spring scheduler every 5 seconds
- **Side effects:** Claims queue items, delegates processing to processor service
- **Throws:** Exceptions propagate to scheduler (logged, next execution continues)
- **Locking:** Uses ShedLock to prevent concurrent execution across instances

```kotlin
@Scheduled(fixedDelay = POLL_INTERVAL_MS)
@SchedulerLock(
    name = "processExecutionQueue",
    lockAtMostFor = "4m",
    lockAtLeastFor = "10s"
)
fun processQueue()
```

**Flow:**
1. Acquire distributed lock (ShedLock)
2. Claim batch of pending items (via processor.claimBatch())
3. If batch empty: return early
4. For each item: delegate to processor.processItem()
5. Release lock when complete

#### `recoverStaleItems()`

- **Purpose:** Recover queue items stuck in CLAIMED state (dispatcher crashed before processing)
- **When to use:** Called automatically by Spring scheduler every minute
- **Side effects:** Releases stale CLAIMED items back to PENDING status
- **Throws:** Exceptions propagate to scheduler (logged, next execution continues)
- **Locking:** Uses ShedLock to prevent concurrent recovery across instances

```kotlin
@Scheduled(fixedDelay = 60000) // Every minute
@SchedulerLock(
    name = "recoverStaleQueueItems",
    lockAtMostFor = "2m",
    lockAtLeastFor = "30s"
)
fun recoverStaleItems()
```

**Flow:**
1. Acquire distributed lock (ShedLock)
2. Call workflowExecutionQueueService.recoverStaleItems(5 minutes)
3. Log count of recovered items if any
4. Release lock when complete

---

## Key Logic

### Core Algorithm / Business Rules

**Batch Processing Loop:**

```
1. Acquire ShedLock (prevents concurrent processing)
   └─> lockAtMostFor: 4 minutes (safety timeout)
   └─> lockAtLeastFor: 10 seconds (minimum hold time)

2. Claim batch of pending items (SKIP LOCKED)
   └─> Returns up to BATCH_SIZE (10) items

3. If batch empty:
   └─> Return early (no work to do)

4. For each item in batch:
   └─> processor.processItem(item)
       └─> Each item processed in REQUIRES_NEW transaction
       └─> Failures isolated per item

5. Release lock (automatic at method return)

6. Wait POLL_INTERVAL_MS (5 seconds) before next iteration
```

**Recovery Logic:**

```
1. Acquire ShedLock (prevents concurrent recovery)
   └─> lockAtMostFor: 2 minutes
   └─> lockAtLeastFor: 30 seconds

2. Find items in CLAIMED state older than 5 minutes
   └─> These are "stale" (dispatcher likely crashed)

3. Release them back to PENDING status
   └─> Increments attemptCount
   └─> Sets claimedAt to null

4. Log count of recovered items
```

### Transaction Boundaries

**Important:** This service has NO @Transactional annotations.

- **processQueue()**: No transaction - delegates to processor which manages its own
- **recoverStaleItems()**: No transaction - delegates to queue service which manages its own

This design ensures:
- Lock acquisition is not part of a transaction
- Each queue item gets its own isolated transaction (REQUIRES_NEW)
- Batch loop doesn't hold database connections unnecessarily

---

## Configuration

|Property|Purpose|Default|Environment-specific|
|---|---|---|---|
|`riven.workflow.engine.enabled`|Enable/disable workflow engine and dispatcher|`true`|Yes|
|`BATCH_SIZE`|Number of items to claim per batch|`10`|No (constant)|
|`POLL_INTERVAL_MS`|Delay between queue processing runs (ms)|`5000` (5 sec)|No (constant)|

### ShedLock Configuration

|Lock Name|Lock At Most For|Lock At Least For|Purpose|
|---|---|---|---|
|`processExecutionQueue`|4 minutes|10 seconds|Prevent concurrent batch processing|
|`recoverStaleQueueItems`|2 minutes|30 seconds|Prevent concurrent recovery|

**Lock parameters:**
- **lockAtMostFor**: Maximum time lock is held (safety timeout if app crashes)
- **lockAtLeastFor**: Minimum time lock is held (prevents rapid re-acquisition)

**ShedLock table requirement:**
- Table: `shedlock` (or configured name)
- Schema: name (VARCHAR), lock_until (TIMESTAMP), locked_at (TIMESTAMP), locked_by (VARCHAR)
- Must be created before application starts

---

## Error Handling

### Errors Thrown

_This service doesn't throw exceptions - delegates error handling to processor and queue service._

|Error/Exception|When|Expected Handling|
|---|---|---|
|ShedLock acquisition timeout|Another instance holds lock|Spring scheduler retries on next interval|
|Database connection failure|Lock table unavailable|Spring scheduler retries on next interval|
|Processor exceptions|Item processing fails|Processor catches and handles internally|

### Errors Handled

_This service catches no exceptions - all error handling delegated to dependencies._

---

## Observability

### Key Metrics

|Metric|Type|What It Indicates|
|---|---|---|
|processQueue() execution count|Counter|How often dispatcher runs|
|Batch size distribution|Histogram|Queue depth over time|
|Items recovered count|Counter|Frequency of stale item recovery (crashes)|
|Lock acquisition failures|Counter|Contention or ShedLock issues|

### Log Events

|Event|Level|When|Key Fields|
|---|---|---|---|
|Processing N queue items|DEBUG|Batch claimed, before processing|batch size|
|Recovered N stale queue items|INFO|After recovery finds stale items|recovered count|
|Empty batch (implicit)|N/A|No logging when queue empty|N/A|

---

## Gotchas & Edge Cases

> [!warning] ShedLock Table Must Exist
> The ShedLock library requires a dedicated table in the database:
> - Table name: `shedlock` (default) or configured
> - Must exist BEFORE application starts
> - Missing table causes lock acquisition failures
> - Multiple apps can share the same ShedLock table (different lock names)

> [!warning] Fixed Batch Size
> The batch size is hardcoded to 10 items:
> - Not configurable per workspace or environment
> - Large queues take multiple iterations to drain
> - Consider increasing BATCH_SIZE if queue grows unbounded
> - Trade-off: larger batches hold locks longer

> [!warning] No Transaction Context
> This service has NO @Transactional annotation:
> - Lock acquisition happens outside transaction boundaries
> - Each processItem() call creates its own REQUIRES_NEW transaction
> - This is by design - prevents long-running transactions holding locks
> - Do NOT add @Transactional to processQueue() - breaks isolation model

> [!warning] Lock Timeout vs Processing Time
> Lock is held for up to 4 minutes (lockAtMostFor):
> - Batch of 10 items processed sequentially
> - Each item may dispatch to Temporal (network call)
> - Total processing time should stay under 4 minutes
> - If processing exceeds 4 minutes, lock may be acquired by another instance
> - Result: duplicate processing possible (Temporal handles idempotency)

### Known Limitations

- Fixed polling interval (5 seconds) - no backoff or adaptive polling
- No priority queue support (all items processed FIFO)
- Recovery runs every minute regardless of need (could be less frequent)
- Lock contention possible if multiple instances start simultaneously

### Thread Safety / Concurrency

**Thread-safe** with distributed locking:
- ShedLock ensures only one instance processes queue at a time
- Within one instance, items processed sequentially (not parallel)
- Processor uses REQUIRES_NEW transactions for isolation
- No shared mutable state

**Concurrency model:**
- At most ONE dispatcher instance active at any time (ShedLock)
- Recovery can run while dispatcher is processing (different lock)
- Safe to deploy multiple application instances

---

## Flows Involved

|Flow|Role in Flow|
|---|---|
|[[riven/docs/system-design/flows/Flow - Workflow Execution Queueing]]|Orchestrates batch processing loop|
|[[riven/docs/system-design/flows/Flow - Stale Item Recovery]]|Initiates recovery of crashed dispatcher items|

---

## Testing

### Unit Test Coverage

- **Location:** `src/test/kotlin/riven/core/service/workflow/queue/WorkflowExecutionDispatcherServiceTest.kt`
- **Key scenarios covered:**
  - processQueue() claims batch and delegates to processor
  - Empty batch returns early without processing
  - recoverStaleItems() delegates to queue service
  - Processor exceptions don't crash dispatcher loop

### Integration Test Notes

Integration tests should verify:
- ShedLock prevents multiple instances from processing concurrently
- @Scheduled annotations trigger at correct intervals
- Lock table exists and is accessible
- Recovery actually releases stale items back to PENDING

### How to Test Manually

1. Enable workflow engine: `riven.workflow.engine.enabled=true`
2. Create ShedLock table in database
3. Enqueue some workflow executions (via API or direct insert)
4. Start application and watch logs for "Processing N queue items"
5. Verify queue items transition to DISPATCHED
6. To test recovery: kill app while items in CLAIMED state, restart, wait 1 minute

---

## Related

- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Queue Management/Queue Management]] - Parent subdomain
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Queue Management/WorkflowExecutionQueueProcessorService]] - Processes individual items
- [[2. Areas/2.1 Startup & Content/Riven/2. System Design/domains/Workflows/Queue Management/WorkflowExecutionQueueService]] - Queue state management
- [[riven/docs/system-design/infrastructure/Shedlock]] - External library for distributed locking

---

## Dependency Diagram

```mermaid
graph TD
    Dispatcher[WorkflowExecutionDispatcherService]

    Dispatcher --> Processor[WorkflowExecutionQueueProcessorService]
    Dispatcher --> QueueService[WorkflowExecutionQueueService]

    Processor --> QueueService
    Processor --> Temporal[Temporal WorkflowClient]

    Dispatcher -.->|@Scheduled| SpringScheduler[Spring Scheduler]
    Dispatcher -.->|@SchedulerLock| ShedLock[ShedLock Library]

    ShedLock --> Database[(PostgreSQL shedlock table)]

    style Dispatcher fill:#e1f5ff
    style SpringScheduler fill:#f0f0f0
    style ShedLock fill:#fff4e1
    style Database fill:#ffe1e1
```

---

## Scheduling Details

### processQueue() Schedule

```kotlin
@Scheduled(fixedDelay = 5000)
@SchedulerLock(
    name = "processExecutionQueue",
    lockAtMostFor = "4m",
    lockAtLeastFor = "10s"
)
```

- **Trigger:** 5 seconds after previous execution completes
- **Lock held:** Minimum 10 seconds, maximum 4 minutes
- **Behavior:** If lock unavailable, skip this execution and try again in 5 seconds

### recoverStaleItems() Schedule

```kotlin
@Scheduled(fixedDelay = 60000)
@SchedulerLock(
    name = "recoverStaleQueueItems",
    lockAtMostFor = "2m",
    lockAtLeastFor = "30s"
)
```

- **Trigger:** 60 seconds after previous execution completes
- **Lock held:** Minimum 30 seconds, maximum 2 minutes
- **Behavior:** If lock unavailable, skip this execution and try again in 60 seconds

---

## Changelog

|Date|Change|Reason|
|---|---|---|
|2026-02-08|Initial documentation|Phase 1 - Workflows domain documentation|
