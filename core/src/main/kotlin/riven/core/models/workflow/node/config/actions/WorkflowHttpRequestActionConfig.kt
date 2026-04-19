package riven.core.models.workflow.node.config.actions

import com.fasterxml.jackson.annotation.JsonTypeName
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClient
import riven.core.enums.common.icon.IconType
import riven.core.enums.workflow.WorkflowActionType
import riven.core.enums.workflow.WorkflowNodeConfigFieldType
import riven.core.enums.workflow.WorkflowNodeType
import riven.core.models.workflow.engine.state.HttpResponseOutput
import riven.core.models.workflow.engine.state.NodeOutput
import riven.core.models.workflow.engine.state.WorkflowDataStore
import riven.core.models.workflow.node.NodeServiceProvider
import riven.core.models.workflow.node.config.WorkflowActionConfig
import riven.core.models.workflow.node.config.WorkflowNodeConfigField
import riven.core.models.workflow.node.config.WorkflowNodeTypeMetadata
import riven.core.models.workflow.node.config.validation.ConfigValidationError
import riven.core.models.workflow.node.config.validation.ConfigValidationResult
import riven.core.models.workflow.node.service
import riven.core.service.workflow.state.WorkflowNodeConfigValidationService
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * Configuration for HTTP_REQUEST action nodes.
 *
 * ## Configuration Properties
 *
 * @property url URL to request (template-enabled)
 * @property method HTTP method: GET, POST, PUT, DELETE, PATCH
 * @property headers Optional map of HTTP headers (template-enabled values)
 * @property body Optional request body for POST/PUT/PATCH (template-enabled values)
 * @property timeoutSeconds Optional timeout override in seconds
 *
 * ## Example Configuration
 *
 * ```json
 * {
 *   "version": 1,
 *   "type": "ACTION",
 *   "subType": "HTTP_REQUEST",
 *   "url": "https://api.example.com/users",
 *   "method": "POST",
 *   "headers": {
 *     "Content-Type": "application/json",
 *     "X-API-Key": "{{ steps.get_api_key.output.key }}"
 *   },
 *   "body": {
 *     "email": "{{ steps.fetch_lead.output.email }}",
 *     "name": "{{ steps.fetch_lead.output.name }}"
 *   }
 * }
 * ```
 *
 * ## Output
 *
 * Returns map with:
 * - `statusCode`: Int HTTP status code
 * - `headers`: Map<String, String> response headers
 * - `body`: String response body
 * - `url`: String requested URL
 * - `method`: String HTTP method used
 *
 * ## Security
 *
 * - SSRF validation prevents requests to localhost, private IPs, and metadata endpoints
 * - Sensitive headers (Authorization, API keys) are not logged
 */
@Schema(
    name = "WorkflowHttpRequestActionConfig",
    description = "Configuration for HTTP_REQUEST action nodes."
)
@JsonTypeName("workflow_http_request_action")
@JsonDeserialize(using = ValueDeserializer.None::class)
data class WorkflowHttpRequestActionConfig(
    override val version: Int = 1,

    @param:Schema(
        description = "URL to request. Can contain templates like {{ steps.x.output.url }}",
        example = "https://api.example.com/users"
    )
    val url: String,

    @param:Schema(
        description = "HTTP method: GET, POST, PUT, DELETE, PATCH",
        example = "POST",
        allowableValues = ["GET", "POST", "PUT", "DELETE", "PATCH"]
    )
    val method: String,

    @param:Schema(
        description = "Optional HTTP headers. Values can be templates.",
        example = """{"Content-Type": "application/json", "Authorization": "Bearer {{ steps.auth.output.token }}"}""",
        nullable = true
    )
    val headers: Map<String, String>? = null,

    @param:Schema(
        description = "Optional request body for POST/PUT/PATCH. Values can be templates.",
        example = """{"name": "{{ steps.user.output.name }}", "email": "user@example.com"}""",
        nullable = true
    )
    val body: Map<String, String>? = null,

    @param:Schema(
        description = "Optional timeout override in seconds",
        example = "30",
        nullable = true
    )
    val timeoutSeconds: Long? = null

) : WorkflowActionConfig {

    override val type: WorkflowNodeType
        get() = WorkflowNodeType.ACTION

    override val subType: WorkflowActionType
        get() = WorkflowActionType.HTTP_REQUEST

    /**
     * Returns typed fields as a map for template resolution.
     * Used by WorkflowCoordinationService to resolve templates before execution.
     */
    override val config: Map<String, Any?>
        get() = mapOf(
            "url" to url,
            "method" to method,
            "headers" to headers,
            "body" to body,
            "timeoutSeconds" to timeoutSeconds
        )

    override val configSchema: List<WorkflowNodeConfigField>
        get() = Companion.configSchema

    companion object {
        val metadata = WorkflowNodeTypeMetadata(
            label = "HTTP Request",
            description = "Makes an HTTP request to an external URL",
            icon = IconType.SEND,
            category = WorkflowNodeType.ACTION
        )

        private val VALID_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH")
        private val SENSITIVE_HEADERS = setOf(
            "authorization",
            "x-api-key",
            "api-key",
            "cookie",
            "set-cookie"
        )

        val configSchema: List<WorkflowNodeConfigField> = listOf(
            WorkflowNodeConfigField(
                key = "url",
                label = "URL",
                type = WorkflowNodeConfigFieldType.TEMPLATE,
                required = true,
                description = "URL to request (supports templates)",
                placeholder = "https://api.example.com/endpoint"
            ),
            WorkflowNodeConfigField(
                key = "method",
                label = "HTTP Method",
                type = WorkflowNodeConfigFieldType.ENUM,
                required = true,
                description = "HTTP method to use",
                options = mapOf(
                    "GET" to "GET",
                    "POST" to "POST",
                    "PUT" to "PUT",
                    "DELETE" to "DELETE",
                    "PATCH" to "PATCH"
                )
            ),
            WorkflowNodeConfigField(
                key = "headers",
                label = "Headers",
                type = WorkflowNodeConfigFieldType.KEY_VALUE,
                required = false,
                description = "HTTP headers to include in the request"
            ),
            WorkflowNodeConfigField(
                key = "body",
                label = "Request Body",
                type = WorkflowNodeConfigFieldType.KEY_VALUE,
                required = false,
                description = "Request body for POST/PUT/PATCH requests"
            ),
            WorkflowNodeConfigField(
                key = "timeoutSeconds",
                label = "Timeout (seconds)",
                type = WorkflowNodeConfigFieldType.DURATION,
                required = false,
                description = "Optional timeout override in seconds"
            )
        )
    }

    /**
     * Validates this configuration.
     *
     * Checks:
     * - url is not blank and has valid template syntax if template
     * - method is one of GET, POST, PUT, DELETE, PATCH
     * - headers values have valid template syntax
     * - body values have valid template syntax
     * - timeout is non-negative if provided
     *
     * @param injector Spring managed provider to inject services into model
     * @return Validation result with any errors
     */
    override fun validate(injector: NodeServiceProvider): ConfigValidationResult {
        val validationService = injector.service<WorkflowNodeConfigValidationService>()
        val errors = mutableListOf<ConfigValidationError>()

        // Validate URL
        errors.addAll(validationService.validateRequiredString(url, "url"))
        if (url.isNotBlank() && validationService.isTemplate(url)) {
            errors.addAll(validationService.validateTemplateSyntax(url, "url"))
        }

        // Validate method
        if (method.isBlank()) {
            errors.add(ConfigValidationError("method", "HTTP method is required"))
        } else if (method.uppercase() !in VALID_METHODS) {
            errors.add(
                ConfigValidationError(
                    "method",
                    "Invalid HTTP method. Must be one of: ${VALID_METHODS.joinToString()}"
                )
            )
        }

        // Validate headers templates
        if (headers != null) {
            errors.addAll(validationService.validateTemplateMap(headers, "headers"))
        }

        // Validate body templates
        if (body != null) {
            errors.addAll(validationService.validateTemplateMap(body, "body"))
        }

        // Validate timeout
        errors.addAll(validationService.validateOptionalDuration(timeoutSeconds, "timeoutSeconds"))

        return ConfigValidationResult(errors)
    }

    override fun execute(
        dataStore: WorkflowDataStore,
        inputs: Map<String, Any?>,
        services: NodeServiceProvider
    ): NodeOutput {
        // Extract resolved inputs
        val resolvedUrl = inputs["url"] as String
        val resolvedMethod = inputs["method"] as String
        val resolvedHeaders = inputs["headers"] as? Map<*, *> ?: emptyMap<Any, Any>()

        @Suppress("UNCHECKED_CAST")
        val resolvedBody = inputs["body"] as? Map<String, Any?>

        // Validate URL (prevent SSRF)
        validateUrl(resolvedUrl)

        // Get WebClient on-demand
        val webClient = services.service<WebClient>()

        // Execute HTTP request (external service call)
        val response = webClient
            .method(HttpMethod.valueOf(resolvedMethod.uppercase()))
            .uri(resolvedUrl)
            .headers { h ->
                resolvedHeaders.forEach { (key, value) ->
                    if (key is String && value is String) {
                        h.set(key, value)
                    }
                }
            }
            .bodyValue(resolvedBody ?: emptyMap<String, Any?>())
            .retrieve()
            .toEntity(String::class.java)
            .block() ?: throw RuntimeException("HTTP request returned null")

        // Log without sensitive data
        logger.info { "HTTP_REQUEST: $resolvedMethod $resolvedUrl -> ${response.statusCode}" }

        // Return typed output
        return HttpResponseOutput(
            statusCode = response.statusCode.value(),
            headers = response.headers.toSingleValueMap(),
            body = response.body,
            url = resolvedUrl,
            method = resolvedMethod
        )
    }

    /**
     * Validates URL to prevent SSRF attacks.
     * Blocks localhost, private IPs, metadata endpoints.
     */
    private fun validateUrl(url: String) {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("Invalid URL: no host")

        // Block localhost
        if (host in listOf("localhost", "127.0.0.1", "::1")) {
            throw SecurityException("HTTP_REQUEST cannot target localhost")
        }

        // Block private IP ranges (basic check)
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("172.")) {
            throw SecurityException("HTTP_REQUEST cannot target private IP ranges")
        }

        // Block cloud metadata endpoints
        if (host == "169.254.169.254") {
            throw SecurityException("HTTP_REQUEST cannot target cloud metadata endpoints")
        }
    }
}
