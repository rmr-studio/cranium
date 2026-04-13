package riven.core.configuration.resilience

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.circuitbreaker.configure.CircuitBreakerConfiguration
import io.github.resilience4j.circuitbreaker.configure.CircuitBreakerConfigurationProperties
import io.github.resilience4j.common.circuitbreaker.configuration.CommonCircuitBreakerConfigurationProperties
import io.github.resilience4j.fallback.configure.FallbackConfiguration
import io.github.resilience4j.spelresolver.configure.SpelResolverConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker as CircuitBreakerAnnotation

/**
 * Verifies that Resilience4j `@CircuitBreaker` annotations are AOP-intercepted
 * under Phase 03.1 Plan 01's direct-coordinate wiring.
 *
 * Plan 01 dropped `resilience4j-spring-boot3:2.3.0` (no Boot 4 starter exists yet) and
 * replaced it with direct `resilience4j-core + resilience4j-annotations + resilience4j-spring`
 * on top of `spring-boot-starter-aop`. Dropping the starter removes autoconfiguration —
 * without this test, a silent AOP-wiring failure (missing `CircuitBreakerAspect`, missing
 * `CircuitBreakerRegistry`, wrong proxy mode) would only surface in production when a
 * flaky upstream triggers the breaker.
 *
 * Production code currently uses the programmatic `CircuitBreaker.executeRunnable { }`
 * path (see PostHogServiceImpl) rather than `@CircuitBreaker` annotations. This test
 * uses a synthetic annotated bean to prove the **annotation path is functional** so
 * that future `@CircuitBreaker` usage is safe under Boot 4 direct wiring.
 */
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@Import(
    value = [
        CircuitBreakerConfiguration::class,
        FallbackConfiguration::class,
        SpelResolverConfiguration::class,
    ]
)
class Resilience4jDirectWiringTestConfig {

    /**
     * Configure a "testBreaker" instance with a 4-call sliding window at 50% threshold.
     * CircuitBreakerConfiguration consumes this properties bean to materialise the registry +
     * aspect chain, mirroring what the dropped spring-boot3 starter used to autowire.
     */
    @Bean
    fun circuitBreakerConfigurationProperties(): CircuitBreakerConfigurationProperties {
        val props = CircuitBreakerConfigurationProperties()
        val inst = CommonCircuitBreakerConfigurationProperties.InstanceProperties()
            .setFailureRateThreshold(50.0f)
            .setSlidingWindowSize(4)
            .setMinimumNumberOfCalls(4)
            .setWaitDurationInOpenState(java.time.Duration.ofMinutes(1))
        props.instances["testBreaker"] = inst
        return props
    }

    @Bean
    fun annotatedService(): AnnotatedService = AnnotatedService()
}

/**
 * Test double carrying a real `@CircuitBreaker` annotation. Spring AOP must proxy this
 * bean; the breaker registry must record every invocation.
 */
@Component
open class AnnotatedService {
    val invocations: AtomicInteger = AtomicInteger(0)
    var shouldFail: Boolean = false

    @CircuitBreakerAnnotation(name = "testBreaker")
    open fun invoke(): String {
        invocations.incrementAndGet()
        if (shouldFail) throw RuntimeException("forced failure for breaker test")
        return "ok"
    }
}

@SpringBootTest(
    classes = [Resilience4jDirectWiringTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
class Resilience4jDirectWiringTest {

    @Autowired
    private lateinit var registry: CircuitBreakerRegistry

    @Autowired
    private lateinit var service: AnnotatedService

    @BeforeEach
    fun reset() {
        // Reset the breaker window before each test.
        registry.circuitBreaker("testBreaker").reset()
        service.invocations.set(0)
        service.shouldFail = false
    }

    /**
     * Test 1: proves the AOP aspect is active. If `CircuitBreakerAspect` is missing from
     * the context (i.e. `resilience4j-spring` wiring isn't imported), the registry never
     * records the call and this assertion fails — the regression is caught at CI.
     */
    @Test
    fun `@CircuitBreaker annotation routes invocations through the registry`() {
        val result = service.invoke()

        assertThat(result).isEqualTo("ok")
        assertThat(service.invocations.get()).isEqualTo(1)

        val breaker = registry.circuitBreaker("testBreaker")
        assertThat(breaker.metrics.numberOfBufferedCalls).isGreaterThan(0)
        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.CLOSED)
    }

    /**
     * Test 2: proves state-transition semantics hold under direct wiring. Repeated failures
     * must flip the breaker to OPEN and subsequent calls must short-circuit with
     * CallNotPermittedException — confirming the aspect not only records but actually
     * intercepts when the breaker opens.
     */
    @Test
    fun `repeated failures transition the breaker to OPEN and subsequent calls short-circuit`() {
        service.shouldFail = true

        // 4 failures fill the sliding window at 100% failure rate (> 50% threshold)
        repeat(4) {
            try {
                service.invoke()
            } catch (_: RuntimeException) { /* expected */ }
        }

        val breaker = registry.circuitBreaker("testBreaker")
        assertThat(breaker.state).isEqualTo(CircuitBreaker.State.OPEN)

        // Next invocation must be short-circuited by the aspect before reaching the method.
        val beforeInvocations = service.invocations.get()
        assertThrows(io.github.resilience4j.circuitbreaker.CallNotPermittedException::class.java) {
            service.invoke()
        }
        assertThat(service.invocations.get())
            .describedAs("breaker OPEN must prevent method entry")
            .isEqualTo(beforeInvocations)
    }
}
