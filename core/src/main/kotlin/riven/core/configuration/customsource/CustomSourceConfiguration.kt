package riven.core.configuration.customsource

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import riven.core.configuration.properties.CustomSourceConfigurationProperties

/**
 * Registers [CustomSourceConfigurationProperties] so the crypto service and
 * future Phase 2 components can inject it.
 */
@Configuration
@EnableConfigurationProperties(CustomSourceConfigurationProperties::class)
class CustomSourceConfiguration
