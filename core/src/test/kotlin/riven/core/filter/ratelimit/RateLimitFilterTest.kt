package cranium.core.filter.ratelimit

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import io.github.oshai.kotlinlogging.KLogger
import io.micrometer.core.instrument.Counter
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import cranium.core.configuration.properties.RateLimitConfigurationProperties
import cranium.core.enums.common.ApiError
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RateLimitFilterTest {

    private lateinit var properties: RateLimitConfigurationProperties
    private lateinit var bucketCache: Cache<String, Bucket>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var exceededCounter: Counter
    private lateinit var filterErrorCounter: Counter
    private lateinit var kLogger: KLogger
    private lateinit var filter: RateLimitFilter
    private lateinit var filterChain: FilterChain

    private val userId = UUID.fromString("f8b1c2d3-4e5f-6789-abcd-ef0123456789")
    private val otherUserId = UUID.fromString("a1b2c3d4-5e6f-7890-abcd-ef1234567890")

    @BeforeEach
    fun setup() {
        properties = RateLimitConfigurationProperties(
            enabled = true,
            authenticatedRpm = 5,
            anonymousRpm = 3,
            cacheMaxSize = 1000,
            cacheExpireMinutes = 10
        )
        bucketCache = Caffeine.newBuilder()
            .maximumSize(properties.cacheMaxSize)
            .expireAfterAccess(properties.cacheExpireMinutes, TimeUnit.MINUTES)
            .build()
        objectMapper = tools.jackson.databind.json.JsonMapper.builder()
            .addModule(tools.jackson.module.kotlin.KotlinModule.Builder().build())
            .build()
        exceededCounter = mock()
        filterErrorCounter = mock()
        kLogger = mock()
        filterChain = mock()

        filter = RateLimitFilter(
            properties = properties,
            bucketCache = bucketCache,
            objectMapper = objectMapper,
            exceededCounter = exceededCounter,
            filterErrorCounter = filterErrorCounter,
            kLogger = kLogger
        )
    }

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    // ------ Test Helpers ------

    private fun setSecurityContext(subjectId: UUID = userId) {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "HS256")
            .subject(subjectId.toString())
            .build()
        val auth = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun createRequest(
        method: String = "GET",
        uri: String = "/api/v1/test"
    ): MockHttpServletRequest = MockHttpServletRequest(method, uri)

    private fun createFilterWithTrustedProxies(cidrs: List<String>): RateLimitFilter =
        RateLimitFilter(
            properties = properties.copy(trustedProxyCidrs = cidrs),
            bucketCache = bucketCache,
            objectMapper = objectMapper,
            exceededCounter = exceededCounter,
            filterErrorCounter = filterErrorCounter,
            kLogger = kLogger,
        )

    // ------ Feature Flag ------

    @Nested
    inner class FeatureFlag {
        @Test
        fun `disabled flag passes request through without bucket interaction`() {
            val disabledFilter = RateLimitFilter(
                properties = properties.copy(enabled = false),
                bucketCache = bucketCache,
                objectMapper = objectMapper,
                exceededCounter = exceededCounter,
                filterErrorCounter = filterErrorCounter,
                kLogger = kLogger
            )
            val request = createRequest()
            val response = MockHttpServletResponse()

            disabledFilter.doFilter(request, response, filterChain)

            verify(filterChain).doFilter(request, response)
            assertEquals(200, response.status)
            assertTrue(response.getHeader("X-RateLimit-Limit") == null)
        }
    }

    // ------ CORS Preflight ------

    @Nested
    inner class CorsPreflight {
        @Test
        fun `CORS preflight request is passed through without consuming a bucket token`() {
            val request = createRequest(method = "OPTIONS")
            request.addHeader("Origin", "https://app.example.com")
            request.addHeader("Access-Control-Request-Method", "POST")
            request.remoteAddr = "10.0.0.1"
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            verify(filterChain).doFilter(request, response)
            assertTrue(response.getHeader("X-RateLimit-Limit") == null)
        }

        @Test
        fun `OPTIONS request without CORS headers is rate limited normally`() {
            val request = createRequest(method = "OPTIONS")
            request.remoteAddr = "10.0.0.1"
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            verify(filterChain).doFilter(request, response)
            assertNotNull(response.getHeader("X-RateLimit-Limit"))
        }
    }

    // ------ Authenticated Requests ------

    @Nested
    inner class AuthenticatedRequests {
        @Test
        fun `authenticated request under limit passes through with rate limit headers`() {
            setSecurityContext()
            val request = createRequest()
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            verify(filterChain).doFilter(request, response)
            assertEquals("5", response.getHeader("X-RateLimit-Limit"))
            assertNotNull(response.getHeader("X-RateLimit-Remaining"))
            assertNotNull(response.getHeader("X-RateLimit-Reset"))
        }

        @Test
        fun `authenticated request over limit returns 429 with correct response`() {
            setSecurityContext()

            // Exhaust the 5-request bucket
            repeat(5) {
                filter.doFilter(createRequest(), MockHttpServletResponse(), filterChain)
            }

            val request = createRequest()
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, filterChain)

            assertEquals(429, response.status)
            assertNotNull(response.getHeader("Retry-After"))
            verify(exceededCounter).increment()

            val body = objectMapper.readValue<Map<String, Any>>(response.contentAsString)
            assertEquals("RATE_LIMIT_EXCEEDED", body["error"])
            assertEquals("429 TOO_MANY_REQUESTS", body["statusCode"])
            assertTrue((body["message"] as String).startsWith("Too many requests"))
        }
    }

    // ------ Unauthenticated Requests ------

    @Nested
    inner class UnauthenticatedRequests {
        @Test
        fun `unauthenticated request under limit passes through`() {
            val request = createRequest()
            request.remoteAddr = "192.168.1.1"
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, filterChain)

            verify(filterChain).doFilter(request, response)
            assertEquals("3", response.getHeader("X-RateLimit-Limit"))
        }

        @Test
        fun `unauthenticated request over limit returns 429 keyed on IP`() {
            repeat(3) {
                val req = createRequest()
                req.remoteAddr = "10.0.0.1"
                filter.doFilter(req, MockHttpServletResponse(), filterChain)
            }

            val request = createRequest()
            request.remoteAddr = "10.0.0.1"
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, filterChain)

            assertEquals(429, response.status)
        }
    }

    // ------ IP Fallback Chain ------

    @Nested
    inner class IpFallbackChain {
        @Test
        fun `uses CF-Connecting-IP when request is from trusted proxy`() {
            val trustedFilter = createFilterWithTrustedProxies(listOf("127.0.0.1/32"))

            // Exhaust bucket for 1.2.3.4
            repeat(3) {
                val req = createRequest()
                req.remoteAddr = "127.0.0.1"
                req.addHeader("CF-Connecting-IP", "1.2.3.4")
                trustedFilter.doFilter(req, MockHttpServletResponse(), filterChain)
            }

            val request = createRequest()
            request.remoteAddr = "127.0.0.1"
            request.addHeader("CF-Connecting-IP", "1.2.3.4")
            request.addHeader("X-Forwarded-For", "5.6.7.8")
            val response = MockHttpServletResponse()
            trustedFilter.doFilter(request, response, filterChain)

            assertEquals(429, response.status)
        }

        @Test
        fun `uses X-Forwarded-For when CF-Connecting-IP absent and request is from trusted proxy`() {
            val trustedFilter = createFilterWithTrustedProxies(listOf("127.0.0.1/32"))

            // Exhaust bucket for 5.6.7.8 via X-Forwarded-For
            repeat(3) {
                val req = createRequest()
                req.remoteAddr = "127.0.0.1"
                req.addHeader("X-Forwarded-For", "5.6.7.8, 10.0.0.1")
                trustedFilter.doFilter(req, MockHttpServletResponse(), filterChain)
            }

            val request = createRequest()
            request.remoteAddr = "127.0.0.1"
            request.addHeader("X-Forwarded-For", "5.6.7.8, 10.0.0.1")
            val response = MockHttpServletResponse()
            trustedFilter.doFilter(request, response, filterChain)

            assertEquals(429, response.status)
        }

        @Test
        fun `ignores forwarded headers when request is not from trusted proxy`() {
            // Default filter has no trusted proxies — forwarded headers should be ignored.
            // Exhaust bucket for remoteAddr "10.0.0.99"
            repeat(3) {
                val req = createRequest()
                req.remoteAddr = "10.0.0.99"
                req.addHeader("CF-Connecting-IP", "1.2.3.4")
                filter.doFilter(req, MockHttpServletResponse(), filterChain)
            }

            // Same remoteAddr should be blocked even though CF-Connecting-IP differs
            val request = createRequest()
            request.remoteAddr = "10.0.0.99"
            request.addHeader("CF-Connecting-IP", "9.9.9.9")
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, filterChain)

            assertEquals(429, response.status)
        }

        @Test
        fun `uses remoteAddr when no proxy headers present`() {
            repeat(3) {
                val req = createRequest()
                req.remoteAddr = "192.168.0.99"
                filter.doFilter(req, MockHttpServletResponse(), filterChain)
            }

            val request = createRequest()
            request.remoteAddr = "192.168.0.99"
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, filterChain)

            assertEquals(429, response.status)
        }
    }

    // ------ Micrometer Counters ------

    @Nested
    inner class MicrometerCounters {
        @Test
        fun `increments exceeded counter on authenticated 429`() {
            setSecurityContext()
            repeat(5) {
                filter.doFilter(createRequest(), MockHttpServletResponse(), filterChain)
            }

            filter.doFilter(createRequest(), MockHttpServletResponse(), filterChain)

            verify(exceededCounter).increment()
        }

        @Test
        fun `increments exceeded counter on unauthenticated 429`() {
            repeat(3) {
                val req = createRequest()
                req.remoteAddr = "10.10.10.10"
                filter.doFilter(req, MockHttpServletResponse(), filterChain)
            }

            val req = createRequest()
            req.remoteAddr = "10.10.10.10"
            filter.doFilter(req, MockHttpServletResponse(), filterChain)

            verify(exceededCounter).increment()
        }
    }

    // ------ Token Refill ------

    @Nested
    inner class TokenRefill {
        @Test
        fun `request succeeds after bucket refills`() {
            setSecurityContext()

            // Exhaust
            repeat(5) {
                filter.doFilter(createRequest(), MockHttpServletResponse(), filterChain)
            }

            // Verify exhausted
            val blockedResponse = MockHttpServletResponse()
            filter.doFilter(createRequest(), blockedResponse, filterChain)
            assertEquals(429, blockedResponse.status)

            // Manually invalidate the bucket to simulate time passing (bucket refill)
            bucketCache.invalidateAll()

            val response = MockHttpServletResponse()
            filter.doFilter(createRequest(), response, filterChain)
            assertEquals(200, response.status)
        }
    }

    // ------ Independent Buckets ------

    @Nested
    inner class IndependentBuckets {
        @Test
        fun `two different users have independent rate limit buckets`() {
            // Exhaust user1's bucket
            setSecurityContext(userId)
            repeat(5) {
                filter.doFilter(createRequest(), MockHttpServletResponse(), filterChain)
            }
            val user1Response = MockHttpServletResponse()
            filter.doFilter(createRequest(), user1Response, filterChain)
            assertEquals(429, user1Response.status)

            // user2 should still have tokens
            SecurityContextHolder.clearContext()
            setSecurityContext(otherUserId)
            val user2Response = MockHttpServletResponse()
            filter.doFilter(createRequest(), user2Response, filterChain)
            assertEquals(200, user2Response.status)
        }
    }

    // ------ Fail-Open ------

    @Nested
    inner class FailOpen {
        @Test
        fun `filter exception fails open and increments error counter`() {
            setSecurityContext()
            val throwingCache: Cache<String, Bucket> = mock()
            whenever(throwingCache.get(any(), any())).thenThrow(RuntimeException("cache exploded"))

            val failFilter = RateLimitFilter(
                properties = properties,
                bucketCache = throwingCache,
                objectMapper = objectMapper,
                exceededCounter = exceededCounter,
                filterErrorCounter = filterErrorCounter,
                kLogger = kLogger,
            )

            val request = createRequest()
            val response = MockHttpServletResponse()
            failFilter.doFilter(request, response, filterChain)

            verify(filterChain).doFilter(request, response)
            verify(filterErrorCounter).increment()
            verify(exceededCounter, never()).increment()
        }

        /**
         * Regression test: downstream filter chain exceptions must propagate normally.
         *
         * Previously the try/catch wrapped both the rate-limit logic AND filterChain.doFilter,
         * causing downstream handler exceptions to be swallowed and logged as rate-limit errors.
         * After the fix, only rate-limit logic is inside the try/catch.
         */
        @Test
        fun `downstream filter chain exception propagates and is not caught by rate limit error handler`() {
            setSecurityContext()
            val downstreamException = RuntimeException("downstream handler exploded")
            whenever(filterChain.doFilter(any(), any())).thenThrow(downstreamException)

            val request = createRequest()
            val response = MockHttpServletResponse()

            val thrown = org.junit.jupiter.api.assertThrows<RuntimeException> {
                filter.doFilter(request, response, filterChain)
            }

            assertEquals("downstream handler exploded", thrown.message)
            verify(filterErrorCounter, never()).increment()
        }
    }

    // ------ Remaining Token Decrement ------

    @Nested
    inner class RemainingTokenDecrement {
        @Test
        fun `X-RateLimit-Remaining decrements correctly across requests`() {
            setSecurityContext()

            val remainingValues = mutableListOf<String?>()
            repeat(5) {
                val response = MockHttpServletResponse()
                filter.doFilter(createRequest(), response, filterChain)
                remainingValues.add(response.getHeader("X-RateLimit-Remaining"))
            }

            assertEquals("4", remainingValues[0])
            assertEquals("3", remainingValues[1])
            assertEquals("2", remainingValues[2])
            assertEquals("1", remainingValues[3])
            assertEquals("0", remainingValues[4])
        }
    }
}
