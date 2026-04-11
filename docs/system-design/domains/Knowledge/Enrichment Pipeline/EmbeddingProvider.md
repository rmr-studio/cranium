---
Created: 2026-04-10
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
tags:
  - layer/service
  - component/active
  - architecture/component
---

Part of [[riven/docs/system-design/domains/Knowledge/Enrichment Pipeline/Enrichment Pipeline]]

# EmbeddingProvider

## Purpose
Abstraction over external embedding APIs. Exposes a blocking `generateEmbedding(text)` contract that can be called directly from Temporal activity threads, with two concrete implementations selected at startup via configuration.

## Responsibilities
- Define a provider-agnostic contract for converting text into a fixed-dimension float vector.
- Expose the active model name and dimension count so callers can stamp them onto persisted embeddings.
- Serialise the provider-specific request shape and deserialise the provider-specific response shape into a common `FloatArray`.
- Fail fast with `IllegalStateException` when the upstream API returns an empty payload.

## Dependencies
- [[EnrichmentClientConfiguration]] — supplies the qualified `WebClient` beans (`openaiWebClient`, `ollamaWebClient`) and the `EnrichmentConfigurationProperties` bean that holds model names and base URLs.

## Used By
- [[EnrichmentActivitiesImpl]] — the `generateEmbedding` activity calls `generateEmbedding(text)` on the active provider.
- [[EnrichmentService]] — holds the provider but only reads `getModelName()` to stamp the `embedding_model` column when persisting a row.

## Public Interface

```kotlin
interface EmbeddingProvider {
    fun generateEmbedding(text: String): FloatArray
    fun getModelName(): String
    fun getDimensions(): Int
}
```

The API is intentionally blocking, not `suspend` — it is called from Temporal activity worker threads, which are plain JVM threads and do not run inside a coroutine scope.

## Implementations

| Provider | Class | Activation | Endpoint | Default Model | Default Dimensions |
|---|---|---|---|---|---|
| OpenAI | `OpenAiEmbeddingProvider` | `riven.enrichment.provider=openai` (default; `matchIfMissing=true`) | `POST {baseUrl}/embeddings` | `text-embedding-3-small` | 1536 |
| Ollama | `OllamaEmbeddingProvider` | `riven.enrichment.provider=ollama` (explicit only) | `POST {baseUrl}/api/embed` | `nomic-embed-text` | 1536 |

Both providers are `@Service` beans gated with `@ConditionalOnProperty` so exactly one is active at runtime.

### OpenAI provider

```kotlin
@Service
@ConditionalOnProperty(
    name = ["riven.enrichment.provider"],
    havingValue = "openai",
    matchIfMissing = true
)
class OpenAiEmbeddingProvider(
    @Qualifier("openaiWebClient") private val webClient: WebClient,
    private val properties: EnrichmentConfigurationProperties,
    private val logger: KLogger
) : EmbeddingProvider
```

Request body:

```kotlin
{ "model": "text-embedding-3-small", "input": "<text>" }
```

Response DTOs:

```kotlin
data class OpenAiEmbeddingResponse(val data: List<OpenAiEmbeddingData>)
data class OpenAiEmbeddingData(val embedding: List<Float>)
```

The first entry in `data` is the embedding for the single input. `List<Float>` is converted to `FloatArray` after deserialisation.

### Ollama provider

```kotlin
@Service
@ConditionalOnProperty(
    name = ["riven.enrichment.provider"],
    havingValue = "ollama"
)
class OllamaEmbeddingProvider(
    @Qualifier("ollamaWebClient") private val webClient: WebClient,
    private val properties: EnrichmentConfigurationProperties,
    private val logger: KLogger
) : EmbeddingProvider
```

Request body:

```kotlin
{ "model": "nomic-embed-text", "input": "<text>" }
```

Response DTO:

```kotlin
data class OllamaEmbeddingResponse(val embeddings: List<List<Float>>)
```

Ollama returns an array-of-arrays; `embeddings[0]` is the embedding for the single input. The inner `List<Float>` is converted to `FloatArray`.

## Key Logic

- Both providers issue a single POST per call and block on the reactive response.
- After deserialisation, both run `check(!embedding.isNullOrEmpty()) { "Empty response from {provider} embeddings API" }` and throw `IllegalStateException` on empty payloads.
- Neither provider implements retries. Retry is delegated upstream to the Temporal activity stub on [[EnrichmentWorkflow]], which configures `RetryOptions` at the activity boundary.

## Gotchas

> [!warning] Dimension mismatch requires a schema migration
> The vector dimension is hard-coded in two places: `entity_embeddings.embedding` is `vector(1536)` in SQL, and [[EntityEmbeddingEntity]] uses `@Array(length = 1536)`. Switching to a model with different output dimensions requires a schema migration AND re-embedding every row.

> [!warning] OpenAI auth failures surface at first call
> `OPENAI_API_KEY` is applied to the `openaiWebClient` bean as a default `Authorization: Bearer ...` header in [[EnrichmentClientConfiguration]]. If the variable is unset, startup still succeeds — the first embedding call fails with a 401 from the OpenAI API.

> [!warning] Retries live on the activity, not the client
> Both providers ignore WebClient-level retry policy. Retries are the responsibility of the surrounding Temporal activity stub.

## Adding a New Provider

1. Add a new `@Service` class implementing `EmbeddingProvider`, gated with `@ConditionalOnProperty` and a new `havingValue`.
2. Add a new `@Qualifier`-annotated `WebClient` bean in [[EnrichmentClientConfiguration]].
3. Add a new nested properties class on `EnrichmentConfigurationProperties` for the provider's base URL, API key, and model.
4. Wire the new bean into the provider via constructor injection.

## Related
- [[EnrichmentClientConfiguration]]
- [[EnrichmentActivitiesImpl]]
- [[EnrichmentService]]
- [[Flow - Entity Enrichment Pipeline]]
