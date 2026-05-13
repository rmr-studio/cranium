package cranium.core.service.enrichment.provider

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
import cranium.core.configuration.properties.EnrichmentConfigurationProperties
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

/**
 * Unit tests for OpenAiEmbeddingProvider.
 *
 * Uses direct instantiation with mocked WebClient fluent chain.
 * No Spring context needed — provider has no auth path or @PreAuthorize.
 */
@Suppress("UNCHECKED_CAST")
class OpenAiEmbeddingProviderTest {

    private val logger: KLogger = mock(KLogger::class.java)
    private val webClient: WebClient = mock(WebClient::class.java)
    private val requestBodyUriSpec: WebClient.RequestBodyUriSpec = mock(WebClient.RequestBodyUriSpec::class.java)
    private val requestBodySpec: WebClient.RequestBodySpec = mock(WebClient.RequestBodySpec::class.java)
    private val requestHeadersSpec: WebClient.RequestHeadersSpec<*> = mock(WebClient.RequestHeadersSpec::class.java) as WebClient.RequestHeadersSpec<*>
    private val responseSpec: WebClient.ResponseSpec = mock(WebClient.ResponseSpec::class.java)

    private val properties = EnrichmentConfigurationProperties(
        provider = cranium.core.enums.enrichment.EmbeddingProvider.OPENAI,
        vectorDimensions = 4,
        openai = EnrichmentConfigurationProperties.OpenAiProperties(
            apiKey = "test-key",
            baseUrl = "https://api.openai.com/v1",
            model = "text-embedding-3-small"
        )
    )

    private lateinit var provider: OpenAiEmbeddingProvider

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

        provider = OpenAiEmbeddingProvider(webClient, properties, logger)
    }

    // ------ generateEmbedding ------

    @Nested
    inner class GenerateEmbedding {

        @Test
        fun `returns FloatArray from valid OpenAI response`() {
            val expectedEmbedding = listOf(0.1f, 0.2f, 0.3f, 0.4f)
            val response = OpenAiEmbeddingResponse(
                data = listOf(OpenAiEmbeddingData(embedding = expectedEmbedding))
            )
            val mono = stubMono(response)

            val result = provider.generateEmbedding("test text")

            assertContentEquals(expectedEmbedding.toFloatArray(), result)
        }

        @Test
        fun `throws IllegalStateException when response is null`() {
            stubMono(null)

            assertThrows<IllegalStateException> {
                provider.generateEmbedding("test text")
            }
        }

        @Test
        fun `throws IllegalStateException when data list is empty`() {
            stubMono(OpenAiEmbeddingResponse(data = emptyList()))

            assertThrows<IllegalStateException> {
                provider.generateEmbedding("test text")
            }
        }
    }

    /**
     * Wires the WebClient → bodyToMono → timeout → block chain so tests can supply
     * the resolved value. Mocking [Mono.timeout] to return the same mono is required
     * because the production call now chains `.timeout(Duration)` before `.block()`.
     */
    private fun stubMono(value: OpenAiEmbeddingResponse?): Mono<OpenAiEmbeddingResponse> {
        val mono = mock(Mono::class.java) as Mono<OpenAiEmbeddingResponse>
        whenever(responseSpec.bodyToMono(OpenAiEmbeddingResponse::class.java)).thenReturn(mono)
        whenever(mono.timeout(any<java.time.Duration>())).thenReturn(mono)
        whenever(mono.block()).thenReturn(value)
        return mono
    }

    // ------ getModelName ------

    @Nested
    inner class GetModelName {

        @Test
        fun `returns configured OpenAI model name`() {
            assertEquals("text-embedding-3-small", provider.getModelName())
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
