package riven.core.service.enrichment.provider

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

/**
 * Unit tests for OllamaEmbeddingProvider.
 *
 * Uses direct instantiation with mocked WebClient fluent chain.
 * No Spring context needed — provider has no auth path or @PreAuthorize.
 */
@Suppress("UNCHECKED_CAST")
class OllamaEmbeddingProviderTest {

    private val logger: KLogger = mock(KLogger::class.java)
    private val webClient: WebClient = mock(WebClient::class.java)
    private val requestBodyUriSpec: WebClient.RequestBodyUriSpec = mock(WebClient.RequestBodyUriSpec::class.java)
    private val requestBodySpec: WebClient.RequestBodySpec = mock(WebClient.RequestBodySpec::class.java)
    private val requestHeadersSpec: WebClient.RequestHeadersSpec<*> = mock(WebClient.RequestHeadersSpec::class.java) as WebClient.RequestHeadersSpec<*>
    private val responseSpec: WebClient.ResponseSpec = mock(WebClient.ResponseSpec::class.java)

    private val properties = EnrichmentConfigurationProperties(
        provider = "ollama",
        vectorDimensions = 4,
        ollama = EnrichmentConfigurationProperties.OllamaProperties(
            baseUrl = "http://localhost:11434",
            model = "nomic-embed-text"
        )
    )

    private lateinit var provider: OllamaEmbeddingProvider

    @BeforeEach
    fun setUp() {
        // WebClient.post() -> RequestBodyUriSpec
        whenever(webClient.post()).thenReturn(requestBodyUriSpec)
        // .uri(String) -> RequestBodySpec (note: uri() on RequestBodyUriSpec returns RequestBodySpec)
        whenever(requestBodyUriSpec.uri(any<String>())).thenReturn(requestBodySpec)
        // .bodyValue(any) -> RequestHeadersSpec<*>
        whenever(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec)
        // .retrieve() -> ResponseSpec
        whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)

        provider = OllamaEmbeddingProvider(webClient, properties, logger)
    }

    // ------ generateEmbedding ------

    @Nested
    inner class GenerateEmbedding {

        @Test
        fun `returns FloatArray from valid Ollama response`() {
            val expectedEmbedding = listOf(0.5f, 0.6f, 0.7f, 0.8f)
            val response = OllamaEmbeddingResponse(
                embeddings = listOf(expectedEmbedding)
            )
            val mono = mock(Mono::class.java) as Mono<OllamaEmbeddingResponse>
            whenever(responseSpec.bodyToMono(OllamaEmbeddingResponse::class.java)).thenReturn(mono)
            whenever(mono.block()).thenReturn(response)

            val result = provider.generateEmbedding("test text")

            assertContentEquals(expectedEmbedding.toFloatArray(), result)
        }

        @Test
        fun `throws IllegalStateException when response is null`() {
            val mono = mock(Mono::class.java) as Mono<OllamaEmbeddingResponse>
            whenever(responseSpec.bodyToMono(OllamaEmbeddingResponse::class.java)).thenReturn(mono)
            whenever(mono.block()).thenReturn(null)

            assertThrows<IllegalStateException> {
                provider.generateEmbedding("test text")
            }
        }

        @Test
        fun `throws IllegalStateException when embeddings list is empty`() {
            val response = OllamaEmbeddingResponse(embeddings = emptyList())
            val mono = mock(Mono::class.java) as Mono<OllamaEmbeddingResponse>
            whenever(responseSpec.bodyToMono(OllamaEmbeddingResponse::class.java)).thenReturn(mono)
            whenever(mono.block()).thenReturn(response)

            assertThrows<IllegalStateException> {
                provider.generateEmbedding("test text")
            }
        }
    }

    // ------ getModelName ------

    @Nested
    inner class GetModelName {

        @Test
        fun `returns configured Ollama model name`() {
            assertEquals("nomic-embed-text", provider.getModelName())
        }
    }

    // ------ getDimensions ------

    @Nested
    inner class GetDimensions {

        @Test
        fun `returns configured vector dimensions`() {
            assertEquals(4, provider.getDimensions())
        }
    }
}
