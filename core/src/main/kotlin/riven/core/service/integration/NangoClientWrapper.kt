package riven.core.service.integration

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import riven.core.configuration.properties.NangoConfigurationProperties
import riven.core.exceptions.NangoApiException
import riven.core.exceptions.RateLimitException
import riven.core.exceptions.TransientNangoException
import riven.core.models.integration.NangoConnection
import riven.core.models.integration.NangoConnectionList
import riven.core.models.integration.NangoErrorResponse
import riven.core.models.integration.NangoRecordsPage
import riven.core.models.integration.NangoTriggerSyncRequest
import java.time.Duration

/**
 * Spring WebClient-based HTTP client for Nango REST API.
 *
 * Provides retry logic and rate limit handling for OAuth flows and connection management.
 */
@Service
class NangoClientWrapper(
    @Qualifier("nangoWebClient") private val webClient: WebClient,
    private val nangoProperties: NangoConfigurationProperties
) {
    private val logger = KotlinLogging.logger {}

    private fun ensureConfigured() {
        require(nangoProperties.secretKey.isNotBlank()) {
            "Nango secret key is not configured. Set NANGO_SECRET_KEY environment variable."
        }
    }

    /**
     * Get a specific connection by provider config key and connection ID.
     *
     * @param providerConfigKey The provider configuration key (e.g., "hubspot")
     * @param connectionId The unique connection ID
     * @return The Nango connection details
     * @throws NangoApiException if the API returns a non-retryable client error
     * @throws RateLimitException if rate limited after retries exhausted
     * @throws TransientNangoException if server errors persist after retries
     */
    fun getConnection(providerConfigKey: String, connectionId: String): NangoConnection {
        ensureConfigured()
        return webClient.get()
            .uri("/connection/{connectionId}?provider_config_key={providerConfigKey}",
                connectionId, providerConfigKey)
            .retrieve()
            .withNangoErrorHandling()
            .bodyToMono(NangoConnection::class.java)
            .withNangoRetry()
            .block() ?: throw NangoApiException("Empty response from Nango API", 500)
    }

    /**
     * List all connections.
     *
     * @return List of all Nango connections
     */
    fun listConnections(): NangoConnectionList {
        ensureConfigured()
        return webClient.get()
            .uri("/connections")
            .retrieve()
            .withNangoErrorHandling()
            .bodyToMono(NangoConnectionList::class.java)
            .withNangoRetry()
            .block() ?: NangoConnectionList()
    }

    /**
     * Delete a connection by provider config key and connection ID.
     *
     * @param providerConfigKey The provider configuration key
     * @param connectionId The unique connection ID
     */
    fun deleteConnection(providerConfigKey: String, connectionId: String) {
        ensureConfigured()
        webClient.delete()
            .uri("/connection/{connectionId}?provider_config_key={providerConfigKey}",
                connectionId, providerConfigKey)
            .retrieve()
            .withNangoErrorHandling()
            .toBodilessEntity()
            .withNangoRetry()
            .block()
    }

    // ------ Sync Operations ------

    /**
     * Fetch records from Nango for a specific model.
     *
     * Returns a single page of records. Caller is responsible for pagination
     * by passing the returned nextCursor to subsequent calls.
     *
     * @param providerConfigKey The provider configuration key (e.g. "hubspot")
     * @param connectionId The Nango connection ID
     * @param model The sync model name (e.g. "Contact", "Deal")
     * @param cursor Pagination cursor from a previous response's nextCursor
     * @param modifiedAfter ISO timestamp filter for incremental sync
     * @param limit Maximum number of records per page
     * @return A page of records with optional nextCursor for pagination
     */
    fun fetchRecords(
        providerConfigKey: String,
        connectionId: String,
        model: String,
        cursor: String? = null,
        modifiedAfter: String? = null,
        limit: Int? = null
    ): NangoRecordsPage {
        ensureConfigured()
        return webClient.get()
            .uri { builder ->
                builder.path("/records")
                    .queryParam("model", model)
                    .apply {
                        cursor?.let { queryParam("cursor", it) }
                        modifiedAfter?.let { queryParam("modified_after", it) }
                        limit?.let { queryParam("limit", it) }
                    }
                    .build()
            }
            .header("Connection-Id", connectionId)
            .header("Provider-Config-Key", providerConfigKey)
            .retrieve()
            .withNangoErrorHandling()
            .bodyToMono(NangoRecordsPage::class.java)
            .withNangoRetry()
            .block() ?: throw NangoApiException("Empty response from Nango records API", 500)
    }

    /**
     * Trigger a sync execution for specific sync names on a connection.
     *
     * @param providerConfigKey The provider configuration key
     * @param connectionId The Nango connection ID (optional — triggers for all connections if null)
     * @param syncs List of sync names to trigger
     */
    fun triggerSync(
        providerConfigKey: String,
        connectionId: String? = null,
        syncs: List<String>
    ) {
        ensureConfigured()
        val body = NangoTriggerSyncRequest(
            providerConfigKey = providerConfigKey,
            connectionId = connectionId,
            syncs = syncs
        )
        webClient.post()
            .uri("/sync/trigger")
            .bodyValue(body)
            .retrieve()
            .withNangoErrorHandling()
            .toBodilessEntity()
            .withNangoRetry()
            .block()
    }

    // ------ Private Helpers ------

    /**
     * Adds standardized Nango error handling to a WebClient response spec.
     * Handles 429 (rate limit), 5xx (transient server errors), and 4xx (client errors).
     */
    private fun WebClient.ResponseSpec.withNangoErrorHandling(): WebClient.ResponseSpec {
        return this
            .onStatus({ it.value() == 429 }) { response ->
                val retryAfter = response.headers().asHttpHeaders()
                    .getFirst("Retry-After")?.toLongOrNull() ?: 60
                Mono.error(RateLimitException("Nango rate limited, retry after $retryAfter seconds"))
            }
            .onStatus({ it.is5xxServerError }) { response ->
                response.bodyToMono(NangoErrorResponse::class.java)
                    .flatMap { error ->
                        Mono.error(TransientNangoException(
                            "Nango server error: ${error.error ?: "Unknown"}",
                            response.statusCode().value()
                        ))
                    }
            }
            .onStatus({ it.is4xxClientError }) { response ->
                response.bodyToMono(NangoErrorResponse::class.java)
                    .flatMap { error ->
                        Mono.error(NangoApiException(
                            "Nango API error: ${error.error ?: "Unknown"}",
                            response.statusCode().value()
                        ))
                    }
            }
    }

    /**
     * Adds retry logic for transient failures (rate limits and server errors).
     */
    private fun <T : Any> Mono<T>.withNangoRetry(): Mono<T> {
        return this.retryWhen(
            Retry.backoff(3, Duration.ofSeconds(2))
                .filter { it is RateLimitException || it is TransientNangoException }
                .doBeforeRetry { logger.warn { "Retrying Nango API call: ${it.failure().message}" } }
        )
    }
}
