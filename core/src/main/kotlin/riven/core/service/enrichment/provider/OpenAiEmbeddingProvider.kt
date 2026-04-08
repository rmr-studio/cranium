package riven.core.service.enrichment.provider

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import riven.core.configuration.properties.EnrichmentConfigurationProperties

/**
 * OpenAI implementation of [EmbeddingProvider].
 *
 * Active by default when `riven.enrichment.provider` is not set or is set to "openai".
 * Posts to /v1/embeddings using the configured model and API key.
 *
 * The openaiWebClient bean is pre-configured with the base URL and Authorization header
 * by [EnrichmentClientConfiguration] — no additional headers needed here.
 */
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
) : EmbeddingProvider {

    // ------ Public API ------

    override fun generateEmbedding(text: String): FloatArray {
        logger.debug { "Generating OpenAI embedding for text of length ${text.length}" }

        val request = mapOf("model" to properties.openai.model, "input" to text)
        val response = webClient.post()
            .uri("/embeddings")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OpenAiEmbeddingResponse::class.java)
            .block()

        val embedding = response?.data?.firstOrNull()?.embedding
        check(!embedding.isNullOrEmpty()) { "Empty response from OpenAI embeddings API" }

        return embedding.toFloatArray()
    }

    override fun getModelName(): String = properties.openai.model

    override fun getDimensions(): Int = properties.vectorDimensions
}
