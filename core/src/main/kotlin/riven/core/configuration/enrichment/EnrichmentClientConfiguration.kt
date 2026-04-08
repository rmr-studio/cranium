package riven.core.configuration.enrichment

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import riven.core.configuration.properties.EnrichmentConfigurationProperties

@Configuration
@EnableConfigurationProperties(EnrichmentConfigurationProperties::class)
class EnrichmentClientConfiguration {

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
