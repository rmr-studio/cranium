package cranium.core.configuration.ratelimit

import tools.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bucket
import io.github.oshai.kotlinlogging.KLogger
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import cranium.core.configuration.properties.RateLimitConfigurationProperties
import cranium.core.filter.ratelimit.RateLimitFilter
import java.util.concurrent.TimeUnit

@Configuration
@EnableConfigurationProperties(RateLimitConfigurationProperties::class)
class RateLimitFilterConfiguration {

    // ------ Caffeine Cache ------

    @Bean
    fun rateLimitBucketCache(properties: RateLimitConfigurationProperties): Cache<String, Bucket> =
        Caffeine.newBuilder()
            .maximumSize(properties.cacheMaxSize)
            .expireAfterAccess(properties.cacheExpireMinutes, TimeUnit.MINUTES)
            .build()

    // ------ Micrometer Counters ------

    @Bean
    fun rateLimitExceededCounter(meterRegistry: MeterRegistry): Counter =
        Counter.builder("cranium.rate_limit.exceeded")
            .description("Rate limit exceeded (429) responses")
            .register(meterRegistry)

    @Bean
    fun rateLimitFilterErrorCounter(meterRegistry: MeterRegistry): Counter =
        Counter.builder("cranium.rate_limit.filter_errors")
            .description("Errors in rate limit filter (fail-open)")
            .register(meterRegistry)

    // ------ HTTP Filter ------

    @Bean
    fun rateLimitFilterRegistration(
        properties: RateLimitConfigurationProperties,
        rateLimitBucketCache: Cache<String, Bucket>,
        objectMapper: ObjectMapper,
        rateLimitExceededCounter: Counter,
        rateLimitFilterErrorCounter: Counter,
        logger: KLogger
    ): FilterRegistrationBean<RateLimitFilter> {
        val filter = RateLimitFilter(
            properties = properties,
            bucketCache = rateLimitBucketCache,
            objectMapper = objectMapper,
            exceededCounter = rateLimitExceededCounter,
            filterErrorCounter = rateLimitFilterErrorCounter,
            kLogger = logger
        )
        val registration = FilterRegistrationBean(filter)
        registration.order = -99
        registration.addUrlPatterns("/api/*")
        return registration
    }
}
