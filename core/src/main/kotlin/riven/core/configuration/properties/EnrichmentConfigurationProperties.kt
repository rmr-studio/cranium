package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties

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
