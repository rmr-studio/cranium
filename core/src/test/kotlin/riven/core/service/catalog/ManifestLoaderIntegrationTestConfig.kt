package riven.core.service.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import riven.core.configuration.properties.ManifestConfigurationProperties
import riven.core.repository.catalog.*
import riven.core.repository.integration.IntegrationDefinitionRepository
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.*

/**
 * Minimal Spring configuration for manifest loader integration tests.
 *
 * Loads JPA, JDBC, and Jackson auto-configuration. Excludes security, Temporal,
 * and other unrelated beans. Manually wires the catalog service chain.
 */
@Configuration
@EnableAutoConfiguration(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
    ],
    excludeName = [
        "io.temporal.spring.boot.autoconfigure.ServiceStubsAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.RootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.NonRootNamespaceAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.MetricsScopeAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.OpenTracingAutoConfiguration",
        "io.temporal.spring.boot.autoconfigure.TestServerAutoConfiguration",
    ],
)
@EnableJpaRepositories(basePackages = ["riven.core.repository.catalog", "riven.core.repository.integration"])
@EntityScan("riven.core.entity")
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
@EnableConfigurationProperties(ManifestConfigurationProperties::class)
class ManifestLoaderIntegrationTestConfig {

    @Bean
    fun auditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun dateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    @Bean
    fun logger(): KLogger = Mockito.mock(KLogger::class.java)

    @Bean
    fun manifestScannerService(
        resourcePatternResolver: ResourcePatternResolver,
        objectMapper: ObjectMapper,
        manifestProperties: ManifestConfigurationProperties,
        logger: KLogger
    ) = ManifestScannerService(resourcePatternResolver, objectMapper, manifestProperties, logger)

    @Bean
    fun manifestResolverService(
        objectMapper: ObjectMapper,
        logger: KLogger
    ) = ManifestResolverService(objectMapper, logger)

    @Bean
    fun manifestUpsertService(
        manifestCatalogRepository: ManifestCatalogRepository,
        catalogEntityTypeRepository: CatalogEntityTypeRepository,
        catalogRelationshipRepository: CatalogRelationshipRepository,
        catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository,
        catalogFieldMappingRepository: CatalogFieldMappingRepository,
        catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository,
        objectMapper: ObjectMapper,
        logger: KLogger
    ) = ManifestUpsertService(
        manifestCatalogRepository,
        catalogEntityTypeRepository,
        catalogRelationshipRepository,
        catalogRelationshipTargetRuleRepository,
        catalogFieldMappingRepository,
        catalogSemanticMetadataRepository,
        objectMapper,
        logger
    )

    @Bean
    fun integrationDefinitionStaleSyncService(
        manifestCatalogRepository: ManifestCatalogRepository,
        integrationDefinitionRepository: IntegrationDefinitionRepository,
        logger: KLogger
    ) = IntegrationDefinitionStaleSyncService(
        manifestCatalogRepository,
        integrationDefinitionRepository,
        logger
    )

    @Bean
    fun manifestReconciliationService(
        manifestCatalogRepository: ManifestCatalogRepository,
        logger: KLogger
    ) = ManifestReconciliationService(
        manifestCatalogRepository,
        logger
    )

    @Bean
    fun manifestCatalogHealthIndicator() = ManifestCatalogHealthIndicator()

    @Bean
    fun manifestLoaderService(
        scannerService: ManifestScannerService,
        resolverService: ManifestResolverService,
        upsertService: ManifestUpsertService,
        reconciliationService: ManifestReconciliationService,
        integrationDefinitionStaleSyncService: IntegrationDefinitionStaleSyncService,
        manifestCatalogRepository: ManifestCatalogRepository,
        healthIndicator: ManifestCatalogHealthIndicator,
        manifestProperties: ManifestConfigurationProperties,
        logger: KLogger
    ) = ManifestLoaderService(
        scannerService,
        resolverService,
        upsertService,
        reconciliationService,
        integrationDefinitionStaleSyncService,
        manifestCatalogRepository,
        healthIndicator,
        manifestProperties.copy(autoLoad = false),
        logger
    )

    @Bean
    fun manifestCatalogService(
        manifestCatalogRepository: ManifestCatalogRepository,
        catalogEntityTypeRepository: CatalogEntityTypeRepository,
        catalogRelationshipRepository: CatalogRelationshipRepository,
        catalogRelationshipTargetRuleRepository: CatalogRelationshipTargetRuleRepository,
        catalogSemanticMetadataRepository: CatalogSemanticMetadataRepository,
        catalogFieldMappingRepository: CatalogFieldMappingRepository,
        logger: KLogger
    ) = ManifestCatalogService(
        manifestCatalogRepository,
        catalogEntityTypeRepository,
        catalogRelationshipRepository,
        catalogRelationshipTargetRuleRepository,
        catalogSemanticMetadataRepository,
        catalogFieldMappingRepository,
        logger
    )
}
