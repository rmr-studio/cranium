package riven.core.filter.analytics

import io.github.oshai.kotlinlogging.KLogger
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import riven.core.models.analytics.ApiRequestEvent
import riven.core.service.analytics.PostHogService
import java.util.UUID

/**
 * HTTP filter that automatically captures a `$api_request` PostHog event for every
 * authenticated API request. Extracts method, route template endpoint, status code,
 * latency, userId, workspaceId, and error context set by [riven.core.exceptions.ExceptionHandler].
 *
 * Runs at order -99 (after Spring Security at -100) so that SecurityContext is populated.
 * Event dispatch is delegated to [PostHogService] which handles async queuing internally.
 */
class PostHogCaptureFilter(
    private val postHogService: PostHogService,
    private val kLogger: KLogger
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        EXCLUDED_PREFIXES.any { request.requestURI.startsWith(it) }

    /**
     * Wraps the filter chain in a try-finally block to capture request latency,
     * then delegates event construction and dispatch to [captureRequest].
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTime = System.nanoTime()

        try {
            filterChain.doFilter(request, response)
        } finally {
            val latencyMs = (System.nanoTime() - startTime) / 1_000_000
            captureRequest(request, response, latencyMs)
        }
    }

    // ------ Private Helpers ------

    private fun captureRequest(request: HttpServletRequest, response: HttpServletResponse, latencyMs: Long) {
        val userId = extractUserId() ?: return
        val workspaceId = extractWorkspaceId(request)
        if (workspaceId == null) {
            kLogger.debug { "Skipping PostHog capture: no workspaceId in path for ${request.method} ${request.requestURI}" }
            return
        }

        val endpoint = extractEndpoint(request)
        val statusCode = response.status
        val isError = statusCode >= 400
        val errorClass = if (isError) request.getAttribute(POSTHOG_ERROR_CLASS) as? String else null

        val event = ApiRequestEvent(
            method = request.method,
            endpoint = endpoint,
            statusCode = statusCode,
            latencyMs = latencyMs,
            isError = isError,
            errorClass = errorClass
        )

        try {
            postHogService.capture(userId, workspaceId, event.eventName, event.properties)
        } catch (e: Exception) {
            kLogger.warn { "PostHog capture failed in filter: ${e.message}" }
        }
    }

    private fun extractUserId(): UUID? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        val jwt = (authentication.principal as? Jwt) ?: return null
        return try {
            UUID.fromString(jwt.subject)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractWorkspaceId(request: HttpServletRequest): UUID? {
        val pathVars = request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        ) as? Map<String, String>
        return pathVars?.get("workspaceId")?.let {
            try {
                UUID.fromString(it)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun extractEndpoint(request: HttpServletRequest): String {
        val routeTemplate = request.getAttribute(
            HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        ) as? String
        return routeTemplate ?: normalizeUri(request.requestURI)
    }

    private fun normalizeUri(uri: String): String =
        uri.replace(UUID_PATTERN, "{id}")

    companion object {
        private val UUID_PATTERN =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val EXCLUDED_PREFIXES = listOf(
            "/actuator", "/docs", "/public", "/api/auth", "/error", "/swagger-ui", "/v3/api-docs"
        )
        const val POSTHOG_ERROR_CLASS = "posthog.error.class"
    }
}
