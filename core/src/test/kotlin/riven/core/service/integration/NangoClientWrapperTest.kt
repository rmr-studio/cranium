package cranium.core.service.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.DefaultUriBuilderFactory
import reactor.core.publisher.Mono
import cranium.core.configuration.properties.NangoConfigurationProperties
import cranium.core.models.integration.NangoRecord
import cranium.core.models.integration.NangoRecordAction
import cranium.core.models.integration.NangoRecordMetadata
import cranium.core.models.integration.NangoRecordsPage
import cranium.core.exceptions.NangoApiException
import cranium.core.models.integration.NangoTriggerSyncRequest
import java.net.URI
import java.util.function.Function

/**
 * Unit tests for NangoClientWrapper.fetchRecords() and triggerSync().
 *
 * Uses Mockito to mock the WebClient fluent chain. NangoClientWrapper is instantiated
 * directly (no @SpringBootTest) since it uses module-level KotlinLogging, not injected KLogger.
 */
class NangoClientWrapperTest {

    private lateinit var webClient: WebClient
    private lateinit var wrapper: NangoClientWrapper

    // GET chain mocks
    @Suppress("UNCHECKED_CAST")
    private lateinit var getRequestHeadersUriSpec: WebClient.RequestHeadersUriSpec<*>
    private lateinit var getRequestHeadersSpec: WebClient.RequestHeadersSpec<*>
    private lateinit var getResponseSpec: WebClient.ResponseSpec

    // POST chain mocks
    private lateinit var postRequestBodyUriSpec: WebClient.RequestBodyUriSpec
    private lateinit var postRequestHeadersSpec: WebClient.RequestHeadersSpec<*>
    private lateinit var postResponseSpec: WebClient.ResponseSpec

    @BeforeEach
    fun setup() {
        webClient = mock(WebClient::class.java)
        val properties = NangoConfigurationProperties(
            secretKey = "test-secret-key",
            baseUrl = "https://api.nango.dev"
        )
        wrapper = NangoClientWrapper(webClient, properties)

        // Setup GET chain
        @Suppress("UNCHECKED_CAST")
        getRequestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec::class.java) as WebClient.RequestHeadersUriSpec<*>
        @Suppress("UNCHECKED_CAST")
        getRequestHeadersSpec = mock(WebClient.RequestHeadersSpec::class.java) as WebClient.RequestHeadersSpec<*>
        getResponseSpec = mock(WebClient.ResponseSpec::class.java)

        whenever(webClient.get()).thenReturn(getRequestHeadersUriSpec)
        // Stub uri() to invoke the function with a real UriBuilder and return the headers spec
        doAnswer { invocation ->
            // Execute the URI function with a real builder so the implementation runs normally
            @Suppress("UNCHECKED_CAST")
            val fn = invocation.arguments[0] as Function<org.springframework.web.util.UriBuilder, URI>
            fn.apply(DefaultUriBuilderFactory().builder())
            getRequestHeadersSpec
        }.whenever(getRequestHeadersUriSpec).uri(any<Function<org.springframework.web.util.UriBuilder, URI>>())
        @Suppress("UNCHECKED_CAST")
        doReturn(getRequestHeadersSpec).whenever(getRequestHeadersSpec).header(any(), any())
        whenever(getRequestHeadersSpec.retrieve()).thenReturn(getResponseSpec)
        whenever(getResponseSpec.onStatus(any(), any())).thenReturn(getResponseSpec)

        // Setup POST chain
        postRequestBodyUriSpec = mock(WebClient.RequestBodyUriSpec::class.java)
        @Suppress("UNCHECKED_CAST")
        postRequestHeadersSpec = mock(WebClient.RequestHeadersSpec::class.java) as WebClient.RequestHeadersSpec<*>
        postResponseSpec = mock(WebClient.ResponseSpec::class.java)

        whenever(webClient.post()).thenReturn(postRequestBodyUriSpec)
        whenever(postRequestBodyUriSpec.uri(any<String>())).thenReturn(postRequestBodyUriSpec)
        @Suppress("UNCHECKED_CAST")
        doReturn(postRequestHeadersSpec).whenever(postRequestBodyUriSpec).bodyValue(any())
        whenever(postRequestHeadersSpec.retrieve()).thenReturn(postResponseSpec)
        whenever(postResponseSpec.onStatus(any(), any())).thenReturn(postResponseSpec)

        // Default POST bodiless entity chain
        @Suppress("UNCHECKED_CAST")
        val responseEntityMono = mock(Mono::class.java) as Mono<org.springframework.http.ResponseEntity<Void>>
        whenever(postResponseSpec.toBodilessEntity()).thenReturn(responseEntityMono)
        whenever(responseEntityMono.retryWhen(any())).thenReturn(responseEntityMono)
        whenever(responseEntityMono.block()).thenReturn(null)
    }

    // ------ Helpers ------

    /**
     * Stubs the GET response to return a given NangoRecordsPage.
     */
    private fun stubGetReturning(page: NangoRecordsPage) {
        @Suppress("UNCHECKED_CAST")
        val mono = mock(Mono::class.java) as Mono<NangoRecordsPage>
        whenever(getResponseSpec.bodyToMono(NangoRecordsPage::class.java)).thenReturn(mono)
        whenever(mono.retryWhen(any())).thenReturn(mono)
        whenever(mono.block()).thenReturn(page)
    }

    // ------ FetchRecords Tests ------

    @Nested
    inner class FetchRecordsTests {

        @Test
        fun `fetchRecords sends GET with Connection-Id and Provider-Config-Key headers`() {
            stubGetReturning(NangoRecordsPage())

            wrapper.fetchRecords(
                providerConfigKey = "hubspot",
                connectionId = "conn-123",
                model = "Contact"
            )

            verify(getRequestHeadersSpec).header("Connection-Id", "conn-123")
            verify(getRequestHeadersSpec).header("Provider-Config-Key", "hubspot")
        }

        @Test
        fun `fetchRecords passes cursor, modifiedAfter, and limit as query params when provided`() {
            val capturedUris = mutableListOf<URI>()

            doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val fn = invocation.arguments[0] as Function<org.springframework.web.util.UriBuilder, URI>
                val uri = fn.apply(DefaultUriBuilderFactory().builder())
                capturedUris.add(uri)
                getRequestHeadersSpec
            }.whenever(getRequestHeadersUriSpec).uri(any<Function<org.springframework.web.util.UriBuilder, URI>>())

            stubGetReturning(NangoRecordsPage())

            wrapper.fetchRecords(
                providerConfigKey = "hubspot",
                connectionId = "conn-123",
                model = "Contact",
                cursor = "abc-cursor",
                modifiedAfter = "2024-01-01T00:00:00Z",
                limit = 100
            )

            assertEquals(1, capturedUris.size)
            val uriString = capturedUris[0].toString()
            assertTrue(uriString.contains("model=Contact"), "Expected model=Contact in URI: $uriString")
            assertTrue(uriString.contains("cursor=abc-cursor"), "Expected cursor in URI: $uriString")
            assertTrue(uriString.contains("modified_after="), "Expected modified_after in URI: $uriString")
            assertTrue(uriString.contains("limit=100"), "Expected limit=100 in URI: $uriString")
        }

        @Test
        fun `fetchRecords omits optional query params when null`() {
            val capturedUris = mutableListOf<URI>()

            doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val fn = invocation.arguments[0] as Function<org.springframework.web.util.UriBuilder, URI>
                val uri = fn.apply(DefaultUriBuilderFactory().builder())
                capturedUris.add(uri)
                getRequestHeadersSpec
            }.whenever(getRequestHeadersUriSpec).uri(any<Function<org.springframework.web.util.UriBuilder, URI>>())

            stubGetReturning(NangoRecordsPage())

            wrapper.fetchRecords(
                providerConfigKey = "hubspot",
                connectionId = "conn-123",
                model = "Deal"
            )

            assertEquals(1, capturedUris.size)
            val uriString = capturedUris[0].toString()
            assertTrue(uriString.contains("model=Deal"), "Expected model=Deal in URI: $uriString")
            assertFalse(uriString.contains("cursor"), "Expected no cursor in URI: $uriString")
            assertFalse(uriString.contains("modified_after"), "Expected no modified_after in URI: $uriString")
            assertFalse(uriString.contains("limit"), "Expected no limit in URI: $uriString")
        }

        /**
         * Regression: empty/null Nango response was silently treated as "no records",
         * which could drop sync data. Fix throws NangoApiException instead.
         */
        @Test
        fun `fetchRecords throws NangoApiException when response body is null`() {
            @Suppress("UNCHECKED_CAST")
            val mono = mock(Mono::class.java) as Mono<NangoRecordsPage>
            whenever(getResponseSpec.bodyToMono(NangoRecordsPage::class.java)).thenReturn(mono)
            whenever(mono.retryWhen(any())).thenReturn(mono)
            whenever(mono.block()).thenReturn(null)

            val exception = assertThrows<NangoApiException> {
                wrapper.fetchRecords(
                    providerConfigKey = "hubspot",
                    connectionId = "conn-123",
                    model = "Contact"
                )
            }

            assertTrue(exception.message!!.contains("Empty response"))
        }

        @Test
        fun `fetchRecords deserializes response with records and nextCursor`() {
            val metadata = NangoRecordMetadata(lastAction = NangoRecordAction.ADDED, cursor = "cursor-1")
            val record = NangoRecord(nangoMetadata = metadata)
            val expectedPage = NangoRecordsPage(
                records = listOf(record),
                nextCursor = "next-page-cursor"
            )
            stubGetReturning(expectedPage)

            val result = wrapper.fetchRecords(
                providerConfigKey = "hubspot",
                connectionId = "conn-123",
                model = "Contact"
            )

            assertEquals(1, result.records.size)
            assertEquals(NangoRecordAction.ADDED, result.records[0].nangoMetadata.lastAction)
            assertEquals("next-page-cursor", result.nextCursor)
        }
    }

    // ------ TriggerSync Tests ------

    @Nested
    inner class TriggerSyncTests {

        @Test
        fun `triggerSync sends POST to sync-trigger with NangoTriggerSyncRequest body`() {
            val bodyCaptor = ArgumentCaptor.forClass(Any::class.java)

            wrapper.triggerSync(
                providerConfigKey = "hubspot",
                syncs = listOf("contacts")
            )

            verify(postRequestBodyUriSpec).uri("/sync/trigger")
            verify(postRequestBodyUriSpec).bodyValue(capture(bodyCaptor))
            val capturedBody = bodyCaptor.value as NangoTriggerSyncRequest
            assertEquals("hubspot", capturedBody.providerConfigKey)
            assertEquals(listOf("contacts"), capturedBody.syncs)
        }

        @Test
        fun `triggerSync includes connectionId in request body when provided`() {
            val bodyCaptor = ArgumentCaptor.forClass(Any::class.java)

            wrapper.triggerSync(
                providerConfigKey = "hubspot",
                connectionId = "conn-456",
                syncs = listOf("contacts", "deals")
            )

            verify(postRequestBodyUriSpec).bodyValue(capture(bodyCaptor))
            val capturedBody = bodyCaptor.value as NangoTriggerSyncRequest
            assertEquals("conn-456", capturedBody.connectionId)
            assertEquals(listOf("contacts", "deals"), capturedBody.syncs)
        }

        @Test
        fun `triggerSync omits connectionId from request body when null`() {
            val bodyCaptor = ArgumentCaptor.forClass(Any::class.java)

            wrapper.triggerSync(
                providerConfigKey = "hubspot",
                connectionId = null,
                syncs = listOf("contacts")
            )

            verify(postRequestBodyUriSpec).bodyValue(capture(bodyCaptor))
            val capturedBody = bodyCaptor.value as NangoTriggerSyncRequest
            assertNull(capturedBody.connectionId)
        }

        @Test
        fun `triggerSync completes without error on successful 200 response`() {
            assertDoesNotThrow {
                wrapper.triggerSync(
                    providerConfigKey = "hubspot",
                    syncs = listOf("contacts")
                )
            }
        }
    }
}
