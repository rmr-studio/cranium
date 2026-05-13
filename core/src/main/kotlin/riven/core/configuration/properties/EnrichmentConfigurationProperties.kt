package cranium.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import cranium.core.enums.enrichment.EmbeddingProvider

@ConfigurationProperties(prefix = "cranium.enrichment")
data class EnrichmentConfigurationProperties(
    val provider: EmbeddingProvider = EmbeddingProvider.OPENAI,
    val vectorDimensions: Int = 1536,
    val requestTimeoutSeconds: Long = 30,
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
