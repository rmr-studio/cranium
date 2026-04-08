package riven.core.service.enrichment.provider

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import riven.core.configuration.properties.EnrichmentConfigurationProperties

/**
 * Ollama implementation of [EmbeddingProvider].
 *
 * Active only when `riven.enrichment.provider=ollama` is explicitly configured.
 * Posts to /api/embed using the configured local model.
 *
 * The ollamaWebClient bean is pre-configured with the Ollama base URL
 * by [EnrichmentClientConfiguration].
 */
@Service
@ConditionalOnProperty(
    name = ["riven.enrichment.provider"],
    havingValue = "ollama"
)
class OllamaEmbeddingProvider(
    @Qualifier("ollamaWebClient") private val webClient: WebClient,
    private val properties: EnrichmentConfigurationProperties,
    private val logger: KLogger
) : EmbeddingProvider {

    // ------ Public API ------

    override fun generateEmbedding(text: String): FloatArray {
        logger.debug { "Generating Ollama embedding for text of length ${text.length}" }

        val request = mapOf("model" to properties.ollama.model, "input" to text)
        val response = webClient.post()
            .uri("/api/embed")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OllamaEmbeddingResponse::class.java)
            .block()

        val embedding = response?.embeddings?.firstOrNull()
        check(!embedding.isNullOrEmpty()) { "Empty response from Ollama embeddings API" }

        return embedding.toFloatArray()
    }

    override fun getModelName(): String = properties.ollama.model

    override fun getDimensions(): Int = properties.vectorDimensions
}
