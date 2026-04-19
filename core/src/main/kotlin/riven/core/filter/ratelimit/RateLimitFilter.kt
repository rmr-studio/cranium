package riven.core.filter.ratelimit

import tools.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import io.github.bucket4j.Bucket
import io.github.oshai.kotlinlogging.KLogger
import io.micrometer.core.instrument.Counter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import java.net.InetAddress
import org.springframework.web.filter.OncePerRequestFilter
import riven.core.configuration.properties.RateLimitConfigurationProperties
import riven.core.enums.common.ApiError
import riven.core.models.response.common.ErrorResponse
import java.time.Duration

/**
 * Per-request rate limiting filter using Bucket4j token buckets backed by a Caffeine cache.
 *
 * Authenticated requests are keyed on the JWT subject (user UUID) with a higher limit.
 * Unauthenticated requests are keyed on the client IP with a lower limit. Forwarded IP
 * headers (CF-Connecting-IP, X-Forwarded-For) are only trusted when the request originates
 * from a configured trusted proxy CIDR; otherwise remoteAddr is used.
 *
 * CORS preflight (OPTIONS) requests are passed through without consuming a bucket token.
 * Feature-flagged via `riven.rate-limit.enabled`.
 *
 * Fail-open: any exception in the rate-limit logic logs a warning, increments an error
 * counter, and passes the request through. Downstream filter chain exceptions propagate
 * normally.
 */
class RateLimitFilter(
    private val properties: RateLimitConfigurationProperties,
    private val bucketCache: Cache<String, Bucket>,
    private val objectMapper: ObjectMapper,
    private val exceededCounter: Counter,
    private val filterErrorCounter: Counter,
    private val kLogger: KLogger,
) : OncePerRequestFilter() {

    private val trustedProxyCidrs: List<CidrRange> =
        properties.trustedProxyCidrs.filter { it.isNotBlank() }.map { CidrRange.parse(it) }

    private val publicEndpointMatcher: OrRequestMatcher = PathPatternRequestMatcher.withDefaults().let { builder ->
        OrRequestMatcher(
            builder.matcher("/api/v1/webhooks/nango"),
            builder.matcher("/api/v1/storage/download/{token}"),
            builder.matcher("/api/v1/avatars/**"),
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.enabled || isCorsPreflightRequest(request) || publicEndpointMatcher.matches(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val allowed = try {
            val (key, authenticated) = resolveKey(request)
            val rpm = if (authenticated) properties.authenticatedRpm else properties.anonymousRpm
            val bucket = bucketCache.get(key) { createBucket(rpm) }

            val probe = bucket.tryConsumeAndReturnRemaining(1)

            response.setHeader(HEADER_LIMIT, rpm.toString())
            response.setHeader(HEADER_REMAINING, probe.remainingTokens.toString())
            response.setHeader(HEADER_RESET, (probe.nanosToWaitForRefill / 1_000_000_000 + 1).toString())

            if (probe.isConsumed) {
                true
            } else {
                exceededCounter.increment()
                writeRateLimitResponse(response, probe.nanosToWaitForRefill)
                false
            }
        } catch (e: Exception) {
            kLogger.warn(e) { "Rate limit filter error, failing open: ${e.message}" }
            filterErrorCounter.increment()
            true
        }

        if (allowed) {
            filterChain.doFilter(request, response)
        }
    }

    // ------ Private Helpers ------

    private fun isCorsPreflightRequest(request: HttpServletRequest): Boolean =
        request.method == "OPTIONS"
                && request.getHeader("Origin") != null
                && request.getHeader("Access-Control-Request-Method") != null

    private fun resolveKey(request: HttpServletRequest): Pair<String, Boolean> {
        val jwt = extractJwt()
        if (jwt != null) {
            return "user:${jwt.subject}" to true
        }
        val ip = resolveClientIp(request)
        return "ip:$ip" to false
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        if (!isFromTrustedProxy(request)) {
            return request.remoteAddr
        }
        return request.getHeader(CF_CONNECTING_IP)
            ?: extractFirstForwardedIp(request)
            ?: request.remoteAddr
    }

    private fun isFromTrustedProxy(request: HttpServletRequest): Boolean =
        trustedProxyCidrs.any { it.matches(request.remoteAddr) }

    private fun extractJwt(): Jwt? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        return when (authentication) {
            is JwtAuthenticationToken -> authentication.token
            else -> authentication.principal as? Jwt
        }
    }

    private fun extractFirstForwardedIp(request: HttpServletRequest): String? =
        request.getHeader(X_FORWARDED_FOR)?.split(",")?.firstOrNull()?.trim()?.ifBlank { null }

    private fun createBucket(rpm: Long): Bucket =
        Bucket.builder()
            .addLimit { limit ->
                limit.capacity(rpm).refillGreedy(rpm, Duration.ofMinutes(1)).initialTokens(rpm)
            }
            .build()

    private fun writeRateLimitResponse(response: HttpServletResponse, nanosToWait: Long) {
        val retryAfterSeconds = (nanosToWait / 1_000_000_000 + 1).coerceAtLeast(1)
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.setHeader(HEADER_RETRY_AFTER, retryAfterSeconds.toString())

        val errorResponse = ErrorResponse(
            statusCode = HttpStatus.TOO_MANY_REQUESTS,
            message = "Too many requests. Try again in $retryAfterSeconds seconds.",
            error = ApiError.RATE_LIMIT_EXCEEDED,
        )
        response.writer.write(objectMapper.writeValueAsString(errorResponse))
    }

    /**
     * Lightweight CIDR matcher replacing the deprecated Spring Security IpAddressMatcher.
     */
    private data class CidrRange(val network: ByteArray, val prefixLength: Int) {

        fun matches(address: String): Boolean {
            val addr = InetAddress.getByName(address).address
            if (addr.size != network.size) return false
            val fullBytes = prefixLength / 8
            val remainingBits = prefixLength % 8
            for (i in 0 until fullBytes) {
                if (addr[i] != network[i]) return false
            }
            if (remainingBits > 0) {
                val mask = (0xFF shl (8 - remainingBits)) and 0xFF
                if ((addr[fullBytes].toInt() and mask) != (network[fullBytes].toInt() and mask)) return false
            }
            return true
        }

        companion object {
            fun parse(cidr: String): CidrRange {
                val parts = cidr.split("/")
                val address = InetAddress.getByName(parts[0]).address
                val prefix = if (parts.size == 2) parts[1].toInt() else address.size * 8
                return CidrRange(address, prefix)
            }
        }
    }

    companion object {
        private const val CF_CONNECTING_IP = "CF-Connecting-IP"
        private const val X_FORWARDED_FOR = "X-Forwarded-For"
        private const val HEADER_LIMIT = "X-RateLimit-Limit"
        private const val HEADER_REMAINING = "X-RateLimit-Remaining"
        private const val HEADER_RESET = "X-RateLimit-Reset"
        private const val HEADER_RETRY_AFTER = "Retry-After"
    }
}
