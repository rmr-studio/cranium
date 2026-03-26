package riven.core.configuration.integration

import io.github.oshai.kotlinlogging.KLogger
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import riven.core.configuration.properties.NangoConfigurationProperties
import riven.core.filter.integration.NangoWebhookHmacFilter

/**
 * Registers the NangoWebhookHmacFilter scoped to the Nango webhook endpoint.
 *
 * The filter runs at order 1 (after Spring Security at -100) and is scoped
 * to /api/v1/webhooks/nango only to avoid overhead on other endpoints.
 */
@Configuration
class NangoWebhookFilterConfiguration {

    @Bean
    fun nangoWebhookHmacFilterRegistration(
        nangoProperties: NangoConfigurationProperties,
        logger: KLogger
    ): FilterRegistrationBean<NangoWebhookHmacFilter> {
        val filter = NangoWebhookHmacFilter(nangoProperties, logger)
        val registration = FilterRegistrationBean(filter)
        registration.order = 1
        registration.addUrlPatterns("/api/v1/webhooks/nango")
        return registration
    }
}
