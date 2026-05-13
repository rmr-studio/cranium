package cranium.core.configuration.audit

import cranium.core.configuration.auth.SecurityAuditorAware
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.*

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
class AuditConfig {

    /**
     * Registers a SecurityAuditorAware bean for JPA auditing.
     *
     * This bean is exposed as "auditorProvider" and supplies the current auditor (user)
     * to Spring Data JPA's auditing infrastructure.
     *
     * @return a SecurityAuditorAware instance used to determine the current auditor.
     */
    @Bean
    fun auditorProvider(): SecurityAuditorAware = SecurityAuditorAware()

    /**
     * Provides ZonedDateTime instances for JPA auditing timestamps.
     *
     * This bean ensures that @CreatedDate and @LastModifiedDate annotations
     * receive ZonedDateTime values instead of the default LocalDateTime.
     *
     * @return a DateTimeProvider that supplies the current ZonedDateTime.
     */
    @Bean
    fun dateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }
}