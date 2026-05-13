package cranium.core.configuration.enrichment

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import cranium.core.configuration.properties.EnrichmentConfigurationProperties
import cranium.core.enums.enrichment.EmbeddingProvider

@Configuration
@EnableConfigurationProperties(EnrichmentConfigurationProperties::class)
class EnrichmentClientConfiguration(
    private val properties: EnrichmentConfigurationProperties
) {

    @PostConstruct
    fun validateProperties() {
        if (properties.provider == EmbeddingProvider.OPENAI) {
            require(properties.openai.apiKey.isNotBlank()) {
                "cranium.enrichment.openai.api-key must be set when provider is OPENAI"
            }
        }
    }

    @Bean
    @Qualifier("openaiWebClient")
    fun openaiWebClient(
        builder: WebClient.Builder,
        properties: EnrichmentConfigurationProperties
    ): WebClient {
        return builder
            .baseUrl(properties.openai.baseUrl)
            .defaultHeader("Authorization", "Bearer ${properties.openai.apiKey}")
            .defaultHeader("Content-Type", "application/json")
            .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
            .build()
    }

    @Bean
    @Qualifier("ollamaWebClient")
    fun ollamaWebClient(
        builder: WebClient.Builder,
        properties: EnrichmentConfigurationProperties
    ): WebClient {
        return builder
            .baseUrl(properties.ollama.baseUrl)
            .defaultHeader("Content-Type", "application/json")
            .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
            .build()
    }
}
