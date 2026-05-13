package cranium.core.configuration.integration

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import cranium.core.configuration.properties.NangoConfigurationProperties

@Configuration
@EnableConfigurationProperties(NangoConfigurationProperties::class)
class NangoClientConfiguration {

    @Bean
    @Qualifier("nangoWebClient")
    fun nangoWebClient(
        builder: WebClient.Builder,
        properties: NangoConfigurationProperties
    ): WebClient {
        return builder
            .baseUrl(properties.baseUrl)
            .defaultHeader("Authorization", "Bearer ${properties.secretKey}")
            .defaultHeader("Content-Type", "application/json")
            .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
            .build()
    }
}
