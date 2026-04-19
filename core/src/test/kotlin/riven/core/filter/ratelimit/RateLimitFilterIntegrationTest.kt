package riven.core.filter.ratelimit

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.ActiveProfiles
import riven.core.configuration.analytics.PostHogConfiguration
import riven.core.configuration.ratelimit.RateLimitFilterConfiguration
import riven.core.configuration.util.LoggerConfig
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests verifying RateLimitFilter is wired into the Spring context
 * and ordered correctly relative to the PostHog filter.
 */
@SpringBootTest(
    classes = [
        RateLimitFilterConfiguration::class,
        PostHogConfiguration::class,
        LoggerConfig::class,
        RateLimitFilterIntegrationTest.TestConfig::class
    ]
)
@ActiveProfiles("test")
class RateLimitFilterIntegrationTest {

    @Configuration
    class TestConfig {
        @Bean
        fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()

        @Bean
        fun objectMapper(): ObjectMapper = JsonMapper.builder().build()
    }

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `rate limit filter and configuration are wired in Spring context`() {
        val registration = context.getBean(
            "rateLimitFilterRegistration",
            FilterRegistrationBean::class.java
        )
        assertNotNull(registration)
        assertNotNull(registration.filter)
        assertTrue(registration.filter is RateLimitFilter)
        assertEquals(listOf("/api/*"), registration.urlPatterns.toList())
    }

    @Test
    fun `rate limit filter runs before PostHog filter`() {
        val rateLimitReg = context.getBean(
            "rateLimitFilterRegistration",
            FilterRegistrationBean::class.java
        )
        val postHogReg = context.getBean(
            "postHogCaptureFilterRegistration",
            FilterRegistrationBean::class.java
        )

        assertTrue(
            rateLimitReg.order < postHogReg.order,
            "RateLimitFilter (${rateLimitReg.order}) should run before PostHogCaptureFilter (${postHogReg.order})"
        )
    }
}
