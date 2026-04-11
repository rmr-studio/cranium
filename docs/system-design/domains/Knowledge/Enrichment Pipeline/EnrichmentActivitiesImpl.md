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

# EnrichmentActivitiesImpl

## Purpose
Thin Spring-managed delegation layer that exposes [[EnrichmentService]], [[SemanticTextBuilderService]], and [[EmbeddingProvider]] as Temporal activities. Holds no business logic — each method is a one-line dispatch wrapped in structured logging.

## Interface: EnrichmentActivities

```kotlin
@ActivityInterface
interface EnrichmentActivities {
    @ActivityMethod
    fun fetchEntityContext(queueItemId: UUID): EnrichmentContext

    @ActivityMethod
    fun constructEnrichedText(context: EnrichmentContext): EnrichedTextResult

    @ActivityMethod
    fun generateEmbedding(text: String): FloatArray

    @ActivityMethod
    fun storeEmbedding(queueItemId: UUID, context: EnrichmentContext, embedding: FloatArray, truncated: Boolean)
}
```

## Implementation: EnrichmentActivitiesImpl (Spring bean)

```kotlin
@Component
class EnrichmentActivitiesImpl(
    private val enrichmentService: EnrichmentService,
    private val semanticTextBuilderService: SemanticTextBuilderService,
    private val embeddingProvider: EmbeddingProvider,
    private val logger: KLogger,
) : EnrichmentActivities
```

### Constructor Dependencies

- `enrichmentService: EnrichmentService` — context assembly + embedding persistence.
- `semanticTextBuilderService: SemanticTextBuilderService` — renders the six-section semantic text and applies truncation.
- `embeddingProvider: EmbeddingProvider` — HTTP client that calls the embedding model.
- `logger: KLogger` — constructor-injected prototype-scoped logger from `LoggerConfig`.

### Methods

| Method | Signature | Delegates To |
|---|---|---|
| `fetchEntityContext` | `(queueItemId: UUID): EnrichmentContext` | `enrichmentService.fetchContext()` |
| `constructEnrichedText` | `(context: EnrichmentContext): EnrichedTextResult` | `semanticTextBuilderService.buildText()` |
| `generateEmbedding` | `(text: String): FloatArray` | `embeddingProvider.generateEmbedding()` |
| `storeEmbedding` | `(queueItemId, context, embedding, truncated)` | `enrichmentService.storeEmbedding()` |

### Behaviour

- Thin delegation layer — zero business logic in the activity layer itself.
- Each activity logs structured INFO at start and end, including text length, model name, embedding dimensions, and truncation status.
- Exceptions are intentionally NOT caught — they propagate to Temporal so the workflow's `RetryOptions` take effect (three attempts with exponential backoff).

## Registration

Registered on the `enrichment.embed` task queue via `TemporalWorkerConfiguration.workerFactory()`, separate from `workflows.default` and `identity.match`. See [[TemporalWorkerConfiguration]].

## Gotchas

> [!warning] Let exceptions propagate
> Catching exceptions inside an activity defeats Temporal's retry contract. The workflow's `RetryOptions` only apply to exceptions that escape the activity method.

- Activities run in the Spring context (unlike `EnrichmentWorkflowImpl`) — they have full access to repositories, services, and beans.
- The dedicated `enrichment.embed` queue exists specifically to keep embedding-API latency from stalling identity matching or workflow execution. Do not register these activities on any other queue.

## Related

- [[EnrichmentWorkflow]]
- [[EnrichmentService]]
- [[SemanticTextBuilderService]]
- [[EmbeddingProvider]]
- [[TemporalWorkerConfiguration]]
- [[Flow - Entity Enrichment Pipeline]]
