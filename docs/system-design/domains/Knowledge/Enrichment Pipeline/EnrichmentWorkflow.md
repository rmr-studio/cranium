---
Created: 2026-04-10
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
tags:
  - component/active
  - layer/service
  - architecture/component
---

Part of [[riven/docs/system-design/domains/Knowledge/Enrichment Pipeline/Enrichment Pipeline]]

# EnrichmentWorkflow

## Purpose
Temporal workflow that drives an entity through the four-step enrichment pipeline (fetch context, construct text, generate embedding, store result). One execution per queue item, deduplicated by workflow ID.

## Interface: EnrichmentWorkflow

```kotlin
@WorkflowInterface
interface EnrichmentWorkflow {
    @WorkflowMethod
    fun embed(queueItemId: UUID)

    companion object {
        fun workflowId(queueItemId: UUID): String = "enrichment-embed-$queueItemId"
    }
}
```

- `@WorkflowMethod embed(queueItemId: UUID)` — drives a single queue item through the pipeline. Returns `Unit`; downstream state lives in `entity_embeddings` and the execution queue row.

### Companion Object
- `workflowId(queueItemId: UUID): String` — returns `"enrichment-embed-{queueItemId}"` for Temporal-level deduplication.

## Implementation: EnrichmentWorkflowImpl

```kotlin
open class EnrichmentWorkflowImpl : EnrichmentWorkflow {
    override fun embed(queueItemId: UUID) {
        val context = stub.fetchEntityContext(queueItemId)
        val result = stub.constructEnrichedText(context)
        val embedding = stub.generateEmbedding(result.text)
        stub.storeEmbedding(queueItemId, context, embedding, result.truncated)
    }
}
```

- **NOT a Spring bean** — Temporal manages lifecycle via factory registration in [[TemporalWorkerConfiguration]].
- **No-arg constructor** — Temporal requirement.
- Uses `Workflow.getLogger()` for logging and `Workflow.newActivityStub()` for activity creation. No Spring injection, no direct DB access, no HTTP calls.

### Activity Options

```kotlin
ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofSeconds(60))
    .setRetryOptions(
        RetryOptions.newBuilder()
            .setMaximumAttempts(3)
            .setInitialInterval(Duration.ofSeconds(2))
            .setBackoffCoefficient(2.0)
            .setMaximumInterval(Duration.ofSeconds(30))
            .build()
    )
    .build()
```

| Setting | Value |
|---------|-------|
| startToCloseTimeout | 60s |
| Max attempts | 3 |
| Initial interval | 2s |
| Max interval | 30s |
| Backoff coefficient | 2.0 |

The 60s timeout is generous because embedding API calls (especially Ollama on slow hardware) can take several seconds per request.

### Pipeline Flow

1. **FetchEntityContext** — `stub.fetchEntityContext(queueItemId)` claims the queue item and assembles an `EnrichmentContext` via [[EnrichmentService]].
2. **ConstructEnrichedText** — `stub.constructEnrichedText(context)` renders the 6-section semantic text via [[SemanticTextBuilderService]], applying truncation if the text exceeds budget.
3. **GenerateEmbedding** — `stub.generateEmbedding(result.text)` calls [[EmbeddingProvider]] over HTTP and returns a `FloatArray`.
4. **StoreEmbedding** — `stub.storeEmbedding(queueItemId, context, embedding, result.truncated)` performs a delete+insert upsert into `entity_embeddings` and marks the queue row `COMPLETED`.

There is no short-circuit — every step runs unless the previous one fails after three retries. Because each step is a separate `@ActivityMethod`, they are independently retryable.

## Workflow ID Convention

Workflow ID is `enrichment-embed-{queueItemId}`, set at dispatch via:

```kotlin
WorkflowOptions.newBuilder()
    .setWorkflowId(EnrichmentWorkflow.workflowId(queueItemId))
    .build()
```

This gives Temporal-level deduplication — restarting the same queue item never spawns a duplicate execution.

## Task Queue

Registered on `enrichment.embed` (`TemporalWorkerConfiguration.ENRICHMENT_EMBED_QUEUE`). This queue is isolated from `workflows.default` and `identity.match` so embedding latency cannot stall other workloads.

## Gotchas

> [!warning] Determinism
> `EnrichmentWorkflowImpl` must remain deterministic. No Spring injection, no direct I/O, no `KLogger`. All side effects must go through activity stubs.

- Uses `Workflow.getLogger()` exclusively. Do NOT inject `KLogger` even though every other class in the codebase does — it would break Temporal's determinism contract.
- The 60s `startToCloseTimeout` is per-activity, not workflow-total. Worst case — three retries on each of four activities — is 12 attempts × 60s = 12 minutes of wall time before the workflow gives up.
- Switching to a slower embedding model requires re-evaluating the timeout.

## Related

- [[EnrichmentActivitiesImpl]]
- [[EnrichmentService]]
- [[SemanticTextBuilderService]]
- [[EmbeddingProvider]]
- [[TemporalWorkerConfiguration]]
- [[Flow - Entity Enrichment Pipeline]]
