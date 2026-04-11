package riven.core.enums.enrichment

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Identifies which embedding provider implementation should be active.
 *
 * Bound from `riven.enrichment.provider` in application.yml. Spring's relaxed
 * binding maps lowercase YAML values (e.g. `openai`) onto these enum constants.
 *
 * Note: [org.springframework.boot.autoconfigure.condition.ConditionalOnProperty]
 * is evaluated against the raw property source string, so the
 * `havingValue = "openai"` / `havingValue = "ollama"` matchers in
 * [riven.core.service.enrichment.provider.OpenAiEmbeddingProvider] and
 * [riven.core.service.enrichment.provider.OllamaEmbeddingProvider] continue to
 * work without modification.
 */
enum class EmbeddingProvider {
    @JsonProperty("openai") OPENAI,
    @JsonProperty("ollama") OLLAMA,
}
