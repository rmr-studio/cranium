package riven.core.configuration.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import riven.core.enums.enrichment.EmbeddingProvider

/**
 * Configuration properties for the enrichment pipeline.
 *
 * knowledgeBacklinkCap (CONF-01) caps catalog excerpt count at the assembler boundary;
 * viewPayloadWarnBytes (CONF-01) drives the Phase 3 size-guard warn log when the serialized
 * EntityKnowledgeView exceeds this threshold.
 */
@ConfigurationProperties(prefix = "riven.enrichment")
data class EnrichmentConfigurationProperties(
    val provider: EmbeddingProvider = EmbeddingProvider.OPENAI,
    val vectorDimensions: Int = 1536,
    val requestTimeoutSeconds: Long = 30,
    val knowledgeBacklinkCap: Int = 3,             // CONF-01
    val viewPayloadWarnBytes: Long = 1_048_576L,   // CONF-01 — 1 MiB; Phase 3 uses for size-guard log
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
