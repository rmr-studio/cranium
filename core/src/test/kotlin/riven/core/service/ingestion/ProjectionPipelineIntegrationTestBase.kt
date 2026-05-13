package cranium.core.service.ingestion

import tools.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import cranium.core.entity.entity.EntityAttributeEntity
import cranium.core.entity.entity.EntityEntity
import cranium.core.enums.common.validation.SchemaType
import cranium.core.enums.integration.SourceType
import cranium.core.models.entity.payload.EntityAttributePrimitivePayload
import cranium.core.models.ingestion.ProjectionResult
import cranium.core.repository.entity.*
import cranium.core.repository.integration.ProjectionRuleRepository
import cranium.core.service.catalog.ManifestResolverService
import cranium.core.service.catalog.ManifestScannerService
import cranium.core.service.catalog.ManifestUpsertService
import cranium.core.service.catalog.TemplateInstallationService
import cranium.core.service.entity.EntityAttributeService
import cranium.core.service.entity.type.EntityTypeService
import cranium.core.service.integration.materialization.TemplateMaterializationService
import cranium.core.service.util.SchemaInitializer
import cranium.core.service.util.factory.entity.EntityFactory
import java.time.ZonedDateTime
import java.util.*

/**
 * Singleton PostgreSQL container shared across all projection pipeline integration tests.
 */
object ProjectionTestContainer {
    val instance: PostgreSQLContainer = PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("cranium_e2e")
        .withUsername("test")
        .withPassword("test")

    init {
        instance.start()
    }
}

/**
 * Minimal Spring configuration for projection pipeline integration tests.
 *
 * Loads JPA, JDBC, and all services needed for the full projection pipeline.
 * Excludes security, Temporal, WebSocket, PostHog, and Nango HTTP configurations.
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
@EnableJpaRepositories(basePackages = ["cranium.core.repository"])
@EntityScan("cranium.core.entity")
@org.springframework.boot.context.properties.ConfigurationPropertiesScan(basePackages = ["cranium.core.configuration.properties"])
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "dateTimeProvider")
@ComponentScan(
    basePackages = [
        "cranium.core.service.ingestion",
        "cranium.core.service.entity",
        "cranium.core.service.identity",
        "cranium.core.service.catalog",
        "cranium.core.service.integration.materialization",
        "cranium.core.service.lifecycle",
        "cranium.core.service.schema",
        "cranium.core.configuration.util",
        "cranium.core.configuration.properties",
        "cranium.core.lifecycle",
    ],
    excludeFilters = [
        ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
            classes = [
                cranium.core.service.workflow.queue.WorkflowExecutionQueueService::class,
                cranium.core.service.identity.IdentityMatchQueueProcessorService::class,
                cranium.core.service.identity.IdentityMatchDispatcherService::class,
                // NangoAdapter requires NangoClientWrapper (in cranium.core.service.integration,
                // which is not scanned by this config — Nango HTTP layer is intentionally
                // excluded from the projection pipeline tests).
                cranium.core.service.ingestion.adapter.nango.NangoAdapter::class,
            ]
        ),
    ],
)
class ProjectionPipelineIntegrationTestConfig {

    /**
     * Explicit JPA transaction manager. Boot 4's auto-configured `transactionManager` bean
     * resolves to `DataSourceTransactionManager` here (likely an autoconfig-ordering edge case
     * triggered by this test's selective ComponentScan + `@EnableJpaRepositories`), which opens
     * a JDBC transaction without binding an EntityManager — the inner `EntityManager.persist`
     * call then sees `isActualTransactionActive() == false` and throws `TransactionRequiredException`.
     * Registering `JpaTransactionManager` explicitly bypasses the conflict.
     */
    @Bean
    fun transactionManager(emf: EntityManagerFactory): PlatformTransactionManager =
        JpaTransactionManager(emf)

    @Bean
    fun auditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun dateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<java.time.temporal.TemporalAccessor>(ZonedDateTime.now()) }

    /**
     * Override AuthTokenService with a test implementation that returns a fixed userId.
     * No SecurityContext is available in integration tests, so the real implementation would throw.
     */
    @Bean
    fun authTokenService(logger: KLogger): cranium.core.service.auth.AuthTokenService {
        val testUserId = UUID.fromString("e0000000-0000-0000-0000-000000000099")
        return object : cranium.core.service.auth.AuthTokenService(logger) {
            override fun getUserId(): UUID = testUserId
            override fun getUserEmail(): String = "e2e@test.com"
        }
    }

    /**
     * Mock WorkspaceSecurity bean referenced by PreAuthorize annotations.
     * Always returns true since integration tests do not enforce workspace access control.
     */
    @Bean
    fun workspaceSecurity(): cranium.core.configuration.auth.WorkspaceSecurity {
        val mock = org.mockito.Mockito.mock(cranium.core.configuration.auth.WorkspaceSecurity::class.java)
        org.mockito.Mockito.doReturn(true).`when`(mock).hasWorkspace(org.mockito.kotlin.any())
        org.mockito.Mockito.doReturn(true).`when`(mock)
            .hasWorkspaceRole(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        return mock
    }

    @Bean
    fun workflowExecutionQueueService(): cranium.core.service.workflow.queue.WorkflowExecutionQueueService =
        org.mockito.Mockito.mock(cranium.core.service.workflow.queue.WorkflowExecutionQueueService::class.java)

    @Bean
    fun activityService(
        logger: KLogger,
        activityLogRepository: cranium.core.repository.activity.ActivityLogRepository
    ): cranium.core.service.activity.ActivityService =
        cranium.core.service.activity.ActivityService(logger, activityLogRepository)

    /**
     * Mock EnrichmentQueueService bean — SchemaReconciliationService depends on it for the
     * bulk re-enqueue hook (enqueueByEntityType), but the projection pipeline tests do not
     * exercise enrichment. The full EnrichmentQueueService graph (Temporal client, etc.) is
     * intentionally not wired into this config.
     */
    @Bean
    fun enrichmentQueueService(): cranium.core.service.enrichment.EnrichmentQueueService =
        org.mockito.Mockito.mock(cranium.core.service.enrichment.EnrichmentQueueService::class.java)
}

/**
 * Abstract base class for projection pipeline integration tests.
 *
 * Provides:
 * - Singleton PostgreSQL container with production-identical schema
 * - Catalog auto-seeded with core model definitions (via CoreModelCatalogService on ApplicationReadyEvent)
 * - Helper methods for workspace setup, manifest seeding, template installation, sync simulation
 * - Verification helpers for asserting projection outcomes
 */
@SpringBootTest(
    classes = [ProjectionPipelineIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.main.allow-bean-definition-overriding=true",
        "cranium.manifests.auto-load=false",
        "cranium.connector.enabled=false",
    ]
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ProjectionPipelineIntegrationTestBase {

    // ------ Injected Services ------

    @Autowired
    protected lateinit var entityProjectionService: EntityProjectionService
    @Autowired
    protected lateinit var identityResolutionService: IdentityResolutionService
    @Autowired
    protected lateinit var entityTypeService: EntityTypeService
    @Autowired
    protected lateinit var entityAttributeService: EntityAttributeService
    @Autowired
    protected lateinit var templateInstallationService: TemplateInstallationService
    @Autowired
    protected lateinit var materializationService: TemplateMaterializationService
    @Autowired
    protected lateinit var manifestUpsertService: ManifestUpsertService
    @Autowired
    protected lateinit var manifestScannerService: ManifestScannerService
    @Autowired
    protected lateinit var manifestResolverService: ManifestResolverService
    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    // ------ Injected Repositories ------

    @Autowired
    protected lateinit var entityRepository: EntityRepository
    @Autowired
    protected lateinit var entityTypeRepository: EntityTypeRepository
    @Autowired
    protected lateinit var entityAttributeRepository: EntityAttributeRepository
    @Autowired
    protected lateinit var entityRelationshipRepository: EntityRelationshipRepository
    @Autowired
    protected lateinit var relationshipDefinitionRepository: RelationshipDefinitionRepository
    @Autowired
    protected lateinit var projectionRuleRepository: ProjectionRuleRepository
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    // ------ Test Constants ------

    protected val workspaceId: UUID = UUID.fromString("e0000000-0000-0000-0000-000000000001")
    protected val userId: UUID = UUID.fromString("e0000000-0000-0000-0000-000000000099")

    companion object {
        private var schemaInitialized = false

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val container = ProjectionTestContainer.instance
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }

            if (!schemaInitialized) {
                val dataSource = DriverManagerDataSource(container.jdbcUrl, container.username, container.password)
                SchemaInitializer.initializeSchema(dataSource)
                schemaInitialized = true
            }
        }
    }

    // ------ Setup Helpers ------

    /**
     * Loads integration manifests synchronously without stale reconciliation.
     *
     * auto-load=false disables ManifestLoaderService's background thread (which would
     * reconcile stale entries and mark core models stale — core models are Kotlin objects,
     * not classpath JSON, so they never appear in the scanned set). This method loads only
     * integration manifests via scan → resolve → upsert, leaving core models untouched.
     */
    protected fun loadIntegrationManifests() {
        val scannedIntegrations = manifestScannerService.scanIntegrations()
        for (scanned in scannedIntegrations) {
            val resolved = manifestResolverService.resolveManifest(scanned)
            manifestUpsertService.upsertManifest(resolved)
        }
    }

    protected fun createWorkspaceAndUser() {
        jdbcTemplate.execute(
            """
            INSERT INTO workspaces (id, name, member_count, created_at, updated_at)
            VALUES ('$workspaceId', 'E2E Test Workspace', 1, now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO users (id, email, name, created_at, updated_at)
            VALUES ('$userId', 'e2e@test.com', 'E2E Test User', now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        // System user required by projection pipeline (identity cluster assignment uses UUID(0,0))
        jdbcTemplate.execute(
            """
            INSERT INTO users (id, email, name, created_at, updated_at)
            VALUES ('00000000-0000-0000-0000-000000000000', 'system@internal', 'System', now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO workspace_members (workspace_id, user_id, role)
            VALUES ('$workspaceId', '$userId', 'ADMIN')
            ON CONFLICT DO NOTHING
            """.trimIndent()
        )
    }

    protected fun createIntegrationDefinition(integrationSlug: String): UUID {
        val defId = UUID.nameUUIDFromBytes("integration-def-$integrationSlug".toByteArray())
        jdbcTemplate.execute(
            """
            INSERT INTO integration_definitions (id, slug, name, category, nango_provider_key, created_at, updated_at)
            VALUES ('$defId', '$integrationSlug', '${integrationSlug.replaceFirstChar { it.uppercase() }}', 'CRM', '$integrationSlug-provider', now(), now())
            ON CONFLICT (slug) DO NOTHING
            """.trimIndent()
        )
        return defId
    }

    protected fun installCoreModelTemplate(templateKey: String) {
        templateInstallationService.installTemplate(workspaceId, templateKey)
    }

    protected fun materializeIntegration(integrationSlug: String, integrationDefinitionId: UUID) {
        materializationService.materializeIntegrationTemplates(workspaceId, integrationSlug, integrationDefinitionId)
    }

    protected fun simulateSync(
        entityTypeId: UUID,
        records: List<Pair<String, Map<UUID, EntityAttributePrimitivePayload>>>,
    ): List<UUID> {
        val entityType = entityTypeRepository.findById(entityTypeId).orElseThrow()
        val entityIds = mutableListOf<UUID>()
        for ((externalId, attributes) in records) {
            val entity = entityRepository.save(
                EntityFactory.createEntityEntity(
                    workspaceId = workspaceId,
                    typeId = entityTypeId,
                    typeKey = entityType.key,
                    identifierKey = entityType.identifierKey,
                    sourceType = SourceType.INTEGRATION,
                    sourceExternalId = externalId,
                    firstSyncedAt = ZonedDateTime.now(),
                    lastSyncedAt = ZonedDateTime.now(),
                )
            )
            val entityId = requireNotNull(entity.id)
            entityIds.add(entityId)
            if (attributes.isNotEmpty()) {
                entityAttributeService.saveAttributes(
                    entityId = entityId,
                    workspaceId = workspaceId,
                    typeId = entityTypeId,
                    attributes = attributes,
                )
            }
        }
        return entityIds
    }

    protected fun executeProjection(entityTypeId: UUID, entityIds: List<UUID>): ProjectionResult {
        return entityProjectionService.processProjections(
            syncedEntityIds = entityIds,
            workspaceId = workspaceId,
            sourceEntityTypeId = entityTypeId,
        )
    }

    // ------ Verification Helpers ------

    protected fun countEntities(entityTypeId: UUID): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entities WHERE type_id = ? AND workspace_id = ? AND deleted = false",
            Long::class.java,
            entityTypeId, workspaceId,
        )!!
    }

    protected fun findProjectedEntities(entityTypeId: UUID): List<EntityEntity> {
        return entityRepository.findByTypeId(entityTypeId)
            .filter { it.sourceType == SourceType.PROJECTED && it.workspaceId == workspaceId }
    }

    protected fun findEntityTypeByKey(key: String): cranium.core.entity.entity.EntityTypeEntity {
        return entityTypeRepository.findByworkspaceIdAndKey(workspaceId, key).orElseThrow {
            AssertionError("Entity type '$key' not found in workspace $workspaceId")
        }
    }

    protected fun getEntityAttributes(entityId: UUID): List<EntityAttributeEntity> {
        return entityAttributeRepository.findByEntityId(entityId)
    }

    protected fun textAttribute(value: String): EntityAttributePrimitivePayload {
        return EntityAttributePrimitivePayload(value = mapOf("value" to value), schemaType = SchemaType.TEXT)
    }

    protected fun emailAttribute(value: String): EntityAttributePrimitivePayload {
        return EntityAttributePrimitivePayload(value = mapOf("value" to value), schemaType = SchemaType.EMAIL)
    }

    protected fun truncateProjectionData() {
        jdbcTemplate.execute("DELETE FROM entity_relationships WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entity_attributes WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entities WHERE workspace_id = '$workspaceId'")
    }
}
