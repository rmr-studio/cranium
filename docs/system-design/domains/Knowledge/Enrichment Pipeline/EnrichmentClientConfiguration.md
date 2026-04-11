---
Created: 2026-04-10
Domains:
  - "[[riven/docs/system-design/domains/Knowledge/Knowledge]]"
tags:
  - layer/configuration
  - component/active
  - architecture/component
---

Part of [[riven/docs/system-design/domains/Knowledge/Enrichment Pipeline/Enrichment Pipeline]]

# EnrichmentClientConfiguration

## Purpose
Spring `@Configuration` class that wires the qualified `WebClient` beans used by the embedding providers, plus the `@ConfigurationProperties` data class that drives provider selection and per-provider settings.

## Responsibilities
- Register two qualified `WebClient` beans — one for OpenAI, one for Ollama — with provider-specific base URLs, default headers, and codec limits.
- Enable and bind `EnrichmentConfigurationProperties` so the provider classes can inject a single typed properties bean.
- Centralise the environment-variable-driven configuration surface for the enrichment pipeline.

## Dependencies
- `WebClient.Builder` — the Spring auto-configured builder used as the base for both client beans.
- `EnrichmentConfigurationProperties` — bound from the `riven.enrichment` prefix via `@EnableConfigurationProperties`.

## Used By
- [[EmbeddingProvider]] — both `OpenAiEmbeddingProvider` and `OllamaEmbeddingProvider` inject their respective qualified `WebClient` and the shared `EnrichmentConfigurationProperties` bean.
- [[EnrichmentService]] — receives `vectorDimensions` indirectly through the active `EmbeddingProvider`.

## Configuration Class

```kotlin
@Configuration
@EnableConfigurationProperties(EnrichmentConfigurationProperties::class)
class EnrichmentClientConfiguration {

    @Bean
    @Qualifier("openaiWebClient")
    fun openaiWebClient(builder: WebClient.Builder, properties: EnrichmentConfigurationProperties): WebClient

    @Bean
    @Qualifier("ollamaWebClient")
    fun ollamaWebClient(builder: WebClient.Builder, properties: EnrichmentConfigurationProperties): WebClient
}
```

## Bean Reference

| Bean | Qualifier | Base URL | Default Headers | Codec Limit |
|---|---|---|---|---|
| `openaiWebClient` | `openaiWebClient` | `properties.openai.baseUrl` | `Authorization: Bearer {apiKey}`, `Content-Type: application/json` | 2 MB max in-memory |
| `ollamaWebClient` | `ollamaWebClient` | `properties.ollama.baseUrl` | `Content-Type: application/json` | 2 MB max in-memory |

## Properties Class

```kotlin
@ConfigurationProperties(prefix = "riven.enrichment")
data class EnrichmentConfigurationProperties(
    val provider: String = "openai",
    val vectorDimensions: Int = 1536,
    val openai: OpenAiProperties = OpenAiProperties(),
    val ollama: OllamaProperties = OllamaProperties()
) {
    data class OpenAiProperties(
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "text-embedding-3-small"
    )

    data class OllamaProperties(
        val baseUrl: String = "http://localhost:11434",
        val model: String = "nomic-embed-text"
    )
}
```

## Property Reference

| YAML key | Default | Env var |
|---|---|---|
| `riven.enrichment.provider` | `openai` | `ENRICHMENT_PROVIDER` |
| `riven.enrichment.vector-dimensions` | `1536` | `ENRICHMENT_VECTOR_DIMENSIONS` |
| `riven.enrichment.openai.api-key` | `""` | `OPENAI_API_KEY` |
| `riven.enrichment.openai.base-url` | `https://api.openai.com/v1` | `OPENAI_BASE_URL` |
| `riven.enrichment.openai.model` | `text-embedding-3-small` | `OPENAI_EMBEDDING_MODEL` |
| `riven.enrichment.ollama.base-url` | `http://localhost:11434` | `OLLAMA_BASE_URL` |
| `riven.enrichment.ollama.model` | `nomic-embed-text` | `OLLAMA_EMBEDDING_MODEL` |

## application.yml

```yaml
riven:
  enrichment:
    provider: ${ENRICHMENT_PROVIDER:openai}
    vector-dimensions: ${ENRICHMENT_VECTOR_DIMENSIONS:1536}
    openai:
      api-key: ${OPENAI_API_KEY:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
      model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: ${OLLAMA_EMBEDDING_MODEL:nomic-embed-text}
```

## Gotchas

> [!warning] OPENAI_API_KEY has no default
> If the OpenAI provider is active and `OPENAI_API_KEY` is unset, the app starts successfully and the first embedding call returns a 401 from the OpenAI API. This is a runtime failure, not a startup failure.

> [!warning] Both WebClient beans are always created
> `@Bean` methods on this class are not conditional. Both `openaiWebClient` and `ollamaWebClient` are instantiated at startup regardless of which provider is active. Only the `@ConditionalOnProperty` on the provider classes themselves decides which one actually issues requests.

> [!warning] 2 MB codec ceiling
> The in-memory buffer limit is fine for a single embedding response from either provider, but worth keeping in mind if a future provider returns batched embeddings in one response.

> [!warning] vectorDimensions is informational
> The `vectorDimensions` property is bound but not currently consumed by any runtime code path. The column type (`vector(1536)`) and the entity annotation (`@Array(length = 1536)`) are hard-coded. Changing the property does not change the column.

## Related
- [[EmbeddingProvider]]
- [[EnrichmentService]]
- [[Flow - Entity Enrichment Pipeline]]
