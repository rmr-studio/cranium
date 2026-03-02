package riven.core.configuration.analytics

import com.posthog.server.PostHog
import com.posthog.server.PostHogConfig
import com.posthog.server.PostHogInterface
import io.github.oshai.kotlinlogging.KLogger
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import riven.core.filter.analytics.PostHogCaptureFilter
import riven.core.configuration.properties.PostHogConfigurationProperties
import riven.core.service.analytics.NoOpPostHogService
import riven.core.service.analytics.PostHogService
import riven.core.service.analytics.PostHogServiceImpl
import java.time.Duration

@Configuration
@EnableConfigurationProperties(PostHogConfigurationProperties::class)
class PostHogConfiguration {

    // ------ Micrometer Counters ------

    @Bean
    fun postHogCaptureCounter(meterRegistry: MeterRegistry): Counter =
        Counter.builder("posthog.capture.total")
            .description("Total PostHog capture attempts")
            .register(meterRegistry)

    @Bean
    fun postHogFailureCounter(meterRegistry: MeterRegistry): Counter =
        Counter.builder("posthog.capture.failures")
            .description("Failed PostHog capture attempts")
            .register(meterRegistry)

    @Bean
    fun postHogCircuitOpenCounter(meterRegistry: MeterRegistry): Counter =
        Counter.builder("posthog.capture.circuit_open")
            .description("PostHog calls rejected by circuit breaker")
            .register(meterRegistry)

    // ------ Circuit Breaker ------

    @Bean
    @ConditionalOnProperty(name = ["riven.posthog.enabled"], havingValue = "true")
    fun postHogCircuitBreaker(): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .permittedNumberOfCallsInHalfOpenState(3)
            .build()

        val registry = CircuitBreakerRegistry.of(config)
        return registry.circuitBreaker("posthog")
    }

    // ------ PostHog SDK Client ------

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = ["riven.posthog.enabled"], havingValue = "true")
    fun postHogClient(properties: PostHogConfigurationProperties): PostHogInterface {
        require(properties.apiKey.isNotBlank()) {
            "riven.posthog.api-key (POSTHOG_API_KEY) must be set when riven.posthog.enabled=true"
        }

        val config = PostHogConfig.builder(properties.apiKey)
            .host(properties.host)
            .preloadFeatureFlags(false)
            .sendFeatureFlagEvent(false)
            .flushAt(20)
            .maxQueueSize(1000)
            .flushIntervalSeconds(30)
            .build()

        return PostHog.with(config)
    }

    // ------ Service Beans ------

    @Bean
    @ConditionalOnProperty(name = ["riven.posthog.enabled"], havingValue = "true")
    fun postHogService(
        client: PostHogInterface,
        circuitBreaker: CircuitBreaker,
        postHogCaptureCounter: Counter,
        postHogFailureCounter: Counter,
        postHogCircuitOpenCounter: Counter,
        logger: KLogger
    ): PostHogService = PostHogServiceImpl(
        client = client,
        circuitBreaker = circuitBreaker,
        captureCounter = postHogCaptureCounter,
        failureCounter = postHogFailureCounter,
        circuitOpenCounter = postHogCircuitOpenCounter,
        logger = logger
    )

    @Bean
    @ConditionalOnProperty(name = ["riven.posthog.enabled"], havingValue = "false", matchIfMissing = true)
    fun noOpPostHogService(
        postHogCaptureCounter: Counter
    ): PostHogService = NoOpPostHogService(
        captureCounter = postHogCaptureCounter
    )

    // ------ HTTP Filter ------

    @Bean
    fun postHogCaptureFilterRegistration(
        postHogService: PostHogService,
        logger: KLogger
    ): FilterRegistrationBean<PostHogCaptureFilter> {
        val filter = PostHogCaptureFilter(postHogService, logger)
        val registration = FilterRegistrationBean(filter)
        registration.order = -99  // After Spring Security (-100), so SecurityContext is populated
        registration.addUrlPatterns("/api/*")
        return registration
    }
}
