package cranium.core.models.integration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Response from GET /connection/{connectionId}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoConnection(
    val id: Int? = null,
    val connectionId: String,
    val providerConfigKey: String,
    val provider: String? = null,
    val createdAt: String? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Response from GET /connections
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoConnectionList(
    val connections: List<NangoConnection> = emptyList()
)

/**
 * Request body for POST /connection (create connection)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoCreateConnectionRequest(
    val connectionId: String,
    val providerConfigKey: String,
    val credentials: Map<String, Any>? = null
)

/**
 * Nango error response
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NangoErrorResponse(
    val error: String? = null,
    val type: String? = null,
    val payload: Map<String, Any>? = null
)
