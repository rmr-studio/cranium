package riven.core.models.analytics

sealed interface AnalyticsEvent {
    val eventName: String
    val properties: Map<String, Any>
}

data class ApiRequestEvent(
    val method: String,
    val endpoint: String,
    val statusCode: Int,
    val latencyMs: Long,
    val isError: Boolean = false,
    val errorClass: String? = null
) : AnalyticsEvent {
    override val eventName: String = "\$api_request"
    override val properties: Map<String, Any>
        get() = buildMap {
            put("method", method)
            put("endpoint", endpoint)
            put("statusCode", statusCode)
            put("latencyMs", latencyMs)
            put("isError", isError)
            errorClass?.let { put("errorClass", it) }
        }
}
