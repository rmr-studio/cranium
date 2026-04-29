package riven.core.service.connotation

import io.github.oshai.kotlinlogging.KLogger
import io.temporal.client.WorkflowClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.entity.catalog.CatalogEntityTypeEntity
import riven.core.entity.catalog.ManifestCatalogEntity
import riven.core.entity.entity.EntityAttributeEntity
import riven.core.entity.entity.EntityEntity
import riven.core.entity.entity.EntityTypeEntity
import riven.core.entity.workflow.ExecutionQueueEntity
import riven.core.enums.catalog.ManifestType
import riven.core.enums.common.validation.SchemaType
import riven.core.enums.core.DataType
import riven.core.enums.entity.LifecycleDomain
import riven.core.enums.entity.semantics.SemanticGroup
import riven.core.enums.integration.SourceType
import riven.core.enums.workflow.ExecutionJobType
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.models.catalog.ConnotationSignals
import riven.core.models.catalog.ScaleMappingType
import riven.core.models.catalog.SentimentScale
import riven.core.models.common.validation.Schema
import riven.core.models.connotation.AnalysisTier
import riven.core.models.connotation.ConnotationStatus
import riven.core.models.connotation.SentimentLabel
import riven.core.repository.catalog.CatalogEntityTypeRepository
import riven.core.repository.catalog.ManifestCatalogRepository
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.repository.entity.EntityAttributeRepository
import riven.core.repository.entity.EntityRepository
import riven.core.repository.entity.EntityTypeRepository
import riven.core.repository.workflow.ExecutionQueueRepository
import riven.core.service.auth.AuthTokenService
import riven.core.service.enrichment.EnrichmentService
import riven.core.service.util.SchemaInitializer
import tools.jackson.databind.node.JsonNodeFactory
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Singleton Postgres container shared by the connotation pipeline integration test.
 *
 * The full DB schema is applied via [SchemaInitializer] so the JSONB-path predicate
 * query at the end of the test runs against the production index definitions.
 */
private object ConnotationPipelineTestContainer {
    val instance: PostgreSQLContainer = PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("riven_connotation_pipeline_test")
        .withUsername("test")
        .withPassword("test")

    init {
        instance.start()
    }
}

/**
 * Spring configuration for the Tier 1 connotation pipeline integration test.
 *
 * Loads the real [EnrichmentService] -> [riven.core.service.connotation.ConnotationAnalysisService] ->
 * [riven.core.service.connotation.ConnotationTier1Mapper] chain, the real [riven.core.service.catalog.ManifestCatalogService],
 * the real [riven.core.service.entity.EntityAttributeService], and all repositories. Mocks are limited to:
 *
 * - [WorkflowClient] / Temporal — `analyzeSemantics` does not dispatch a workflow.
 * - `WorkspaceService` / `WorkspaceSecurity` — workspace flag is overridden to enabled in-place via JDBC; security
 *   is short-circuited because Spring Security is excluded from the auto-config and `@PreAuthorize`
 *   decoration is not active without it.
 * - [riven.core.service.activity.ActivityService] — the Activity insert path uses a CHECK constraint
 *   on `operation` that does not yet include `ANALYZE`; mocking keeps the test focused on the
 *   envelope-write path rather than the audit-trail concern.
 * - [riven.core.service.enrichment.provider.EmbeddingProvider] — embedding generation is downstream of
 *   `analyzeSemantics` and out of scope.
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
@EnableJpaRepositories(basePackages = ["riven.core.repository"])
@EntityScan("riven.core.entity")
@EnableJpaAuditing(
    auditorAwareRef = "connotationPipelineAuditorProvider",
    dateTimeProviderRef = "connotationPipelineDateTimeProvider",
)
@org.springframework.context.annotation.Import(
    riven.core.configuration.util.LoggerConfig::class,
    riven.core.service.enrichment.EnrichmentService::class,
    riven.core.service.connotation.ConnotationAnalysisService::class,
    riven.core.service.connotation.ConnotationTier1Mapper::class,
    riven.core.service.catalog.ManifestCatalogService::class,
    riven.core.service.entity.EntityAttributeService::class,
)
class ConnotationPipelineIntegrationTestConfig {

    @Bean
    fun connotationPipelineAuditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun connotationPipelineDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    /**
     * Provide ConfigurationProperties beans directly. Spring Boot 4's component scan
     * eagerly registers `@ConfigurationProperties`-annotated data classes when their
     * package is reachable transitively, leading to duplicate bean registrations when
     * `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan` is also active.
     * Defining them as plain `@Bean`s sidesteps the detection.
     */
    @Bean
    fun connotationAnalysisConfigurationProperties(): riven.core.configuration.properties.ConnotationAnalysisConfigurationProperties =
        riven.core.configuration.properties.ConnotationAnalysisConfigurationProperties()

    @Bean
    fun enrichmentConfigurationProperties(): riven.core.configuration.properties.EnrichmentConfigurationProperties =
        riven.core.configuration.properties.EnrichmentConfigurationProperties()

    @Bean
    fun manifestConfigurationProperties(): riven.core.configuration.properties.ManifestConfigurationProperties =
        riven.core.configuration.properties.ManifestConfigurationProperties(autoLoad = false)

    @Bean
    fun queryConfigurationProperties(): riven.core.configuration.properties.QueryConfigurationProperties =
        riven.core.configuration.properties.QueryConfigurationProperties()

    /** Stub AuthTokenService — no JWT context is set up for integration tests. */
    @Bean
    fun authTokenService(logger: KLogger): AuthTokenService {
        val testUserId = UUID.fromString("c0000000-0000-0000-0000-000000000099")
        return object : AuthTokenService(logger) {
            override fun getUserId(): UUID = testUserId
            override fun getUserEmail(): String = "connotation-e2e@test.com"
        }
    }

    /**
     * `WorkspaceSecurity` is referenced by `@PreAuthorize` in EnrichmentService.analyzeSemantics
     * (which has no `@PreAuthorize` itself, but ConnotationAnalysisService.analyze does). Without
     * Spring Security on the classpath the annotation is ignored, but the bean is still wired
     * via constructor on services that depend on it. Mocked-permissive for safety.
     */
    @Bean
    fun workspaceSecurity(): riven.core.configuration.auth.WorkspaceSecurity {
        val mock = org.mockito.Mockito.mock(riven.core.configuration.auth.WorkspaceSecurity::class.java)
        org.mockito.Mockito.doReturn(true).`when`(mock).hasWorkspace(org.mockito.kotlin.any())
        org.mockito.Mockito.doReturn(true).`when`(mock)
            .hasWorkspaceRole(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        return mock
    }

    /**
     * `WorkspaceService` is heavyweight (UserService, StorageService, ApplicationEventPublisher,
     * ActivityService …). Only [WorkspaceService.isConnotationEnabled] is needed by the
     * SENTIMENT axis path — short-circuit it via a thin mock.
     */
    @Bean
    fun workspaceService(): riven.core.service.workspace.WorkspaceService {
        val mock = org.mockito.Mockito.mock(riven.core.service.workspace.WorkspaceService::class.java)
        org.mockito.Mockito.doReturn(true).`when`(mock).isConnotationEnabled(org.mockito.kotlin.any())
        return mock
    }

    /**
     * `ActivityService` is mocked because the `activity_logs` table CHECK constraint on
     * `operation` does not yet include the `ANALYZE` value emitted by
     * [riven.core.service.connotation.ConnotationAnalysisService.analyze]. The activity-log
     * write path has unit-test coverage in `ConnotationAnalysisServiceTest` already; this
     * integration test focuses on the envelope-persistence path.
     */
    @Bean
    fun activityService(): riven.core.service.activity.ActivityService =
        org.mockito.Mockito.mock(riven.core.service.activity.ActivityService::class.java)

    @Bean
    fun workflowClient(): WorkflowClient = org.mockito.Mockito.mock(WorkflowClient::class.java)

    @Bean
    fun embeddingProvider(): riven.core.service.enrichment.provider.EmbeddingProvider =
        org.mockito.Mockito.mock(riven.core.service.enrichment.provider.EmbeddingProvider::class.java)
}

/**
 * End-to-end Tier 1 connotation pipeline integration test (Phase B Task 18).
 *
 * Verifies that a manifest carrying `connotationSignals` (TIER_1, LINEAR scale `[1,5]` -> `[-1,1]`,
 * theme attributes), combined with a workspace that has opted into connotation analysis and an
 * entity carrying a `satisfaction_score` attribute, produces an `ANALYZED` SENTIMENT axis
 * envelope persisted to `entity_connotation` with the correct score, label, and themes.
 *
 * The manifest loader is intentionally bypassed — the loader has its own unit tests
 * ([riven.core.service.catalog.ManifestUpsertService] etc.). Instead, the catalog row, workspace
 * entity type, entity instance, and `entity_attributes` rows are seeded directly via repositories.
 * This exercises the live runtime chain: [EnrichmentService.analyzeSemantics]
 *  -> `persistConnotationEnvelope`
 *  -> [riven.core.service.connotation.ConnotationAnalysisService.analyze]
 *  -> [riven.core.service.connotation.ConnotationTier1Mapper.analyze]
 *  -> JSONB write to `entity_connotation`.
 *
 * The final assertion runs a Layer-4-style JSONB predicate query
 * (`connotation_metadata->'axes'->'SENTIMENT'->>'sentiment') as float > 0.5`) to prove the
 * persisted shape is queryable. **Axis name is UPPERCASE** in the envelope (per Task 15) — the
 * predicate matches that.
 */
@SpringBootTest(
    classes = [ConnotationPipelineIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.main.allow-bean-definition-overriding=true",
        "riven.manifests.auto-load=false",
        "riven.connector.enabled=false",
    ]
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConnotationPipelineIntegrationTest {

    @Autowired
    private lateinit var enrichmentService: EnrichmentService

    @Autowired
    private lateinit var entityConnotationRepository: EntityConnotationRepository

    @Autowired
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @Autowired
    private lateinit var entityRepository: EntityRepository

    @Autowired
    private lateinit var entityTypeRepository: EntityTypeRepository

    @Autowired
    private lateinit var entityAttributeRepository: EntityAttributeRepository

    @Autowired
    private lateinit var manifestCatalogRepository: ManifestCatalogRepository

    @Autowired
    private lateinit var catalogEntityTypeRepository: CatalogEntityTypeRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val workspaceId: UUID = UUID.fromString("c0000000-0000-0000-0000-00000000c001")
    private val userId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000099")

    private val satisfactionScoreAttrId: UUID = UUID.fromString("c0000000-0000-0000-0000-00000000a001")
    private val tagsAttrId: UUID = UUID.fromString("c0000000-0000-0000-0000-00000000a002")

    companion object {
        private var schemaInitialized = false

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val container = ConnotationPipelineTestContainer.instance
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }

            if (!schemaInitialized) {
                val dataSource = DriverManagerDataSource(
                    container.jdbcUrl, container.username, container.password
                )
                SchemaInitializer.initializeSchema(dataSource)
                schemaInitialized = true
            }
        }
    }

    @BeforeAll
    fun seedWorkspaceAndUser() {
        // Workspace + user shells — entity_attributes, entity_types, entities all FK
        // to workspaces; activity logs would FK to users (mocked here, so user only needs
        // to exist for FK soundness if any other path inserts).
        jdbcTemplate.execute(
            """
            INSERT INTO workspaces (id, name, member_count, connotation_enabled, created_at, updated_at)
            VALUES ('$workspaceId', 'Connotation E2E Workspace', 1, true, now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO users (id, email, name, created_at, updated_at)
            VALUES ('$userId', 'connotation-e2e@test.com', 'Connotation E2E User', now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    @BeforeEach
    fun cleanScopedTables() {
        // Order matters: child tables first.
        jdbcTemplate.execute("DELETE FROM execution_queue WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entity_connotation WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entity_attributes WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entities WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entity_types WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM catalog_entity_types WHERE manifest_id IN (SELECT id FROM manifest_catalog WHERE key = 'zendesk-fixture')")
        jdbcTemplate.execute("DELETE FROM manifest_catalog WHERE key = 'zendesk-fixture'")
    }

    // ------ Test ------

    /**
     * Tier 1 LINEAR scale `[1,5]` -> `[-1,1]` with `satisfaction_score = 5.0` should map to
     * `sentiment = 1.0`, `sentimentLabel = VERY_POSITIVE`, and pass the `tags` value through
     * verbatim as the single theme entry. The other axes (RELATIONAL / STRUCTURAL) must still
     * be populated, and the persisted envelope must be queryable via a JSONB predicate.
     */
    @Test
    fun `Tier 1 LINEAR pipeline produces ANALYZED SENTIMENT axis with correct score and themes`() {
        // 1. Seed catalog manifest + catalog entity type with connotationSignals.
        val manifestId = seedCatalogManifestWithConnotationSignals()

        // 2. Seed workspace entity type wired to the catalog manifest, with attributeKeyMapping
        //    so the SENTIMENT axis can resolve manifest keys -> attribute UUIDs.
        val entityType = seedWorkspaceEntityType(manifestId)
        val entityTypeId = requireNotNull(entityType.id)

        // 3. Seed an entity instance + its two attributes.
        val entity = seedEntity(entityTypeId, entityType.key, entityType.identifierKey)
        val entityId = requireNotNull(entity.id)
        seedAttribute(entityId, entityTypeId, satisfactionScoreAttrId, SchemaType.NUMBER, numberValue(5.0))
        seedAttribute(entityId, entityTypeId, tagsAttrId, SchemaType.TEXT, textValue("billing,fast"))

        // 4. Enqueue the enrichment item.
        val queueItem = executionQueueRepository.save(
            ExecutionQueueEntity(
                workspaceId = workspaceId,
                jobType = ExecutionJobType.ENRICHMENT,
                entityId = entityId,
                status = ExecutionQueueStatus.PENDING,
            )
        )
        val queueItemId = requireNotNull(queueItem.id)

        // 5. Run the analysis hop.
        val context = enrichmentService.analyzeSemantics(queueItemId)

        // 6. Verify the in-memory context surfaces the ANALYZED sentiment.
        assertThat(context.sentiment).isNotNull
        assertThat(context.sentiment!!.status).isEqualTo(ConnotationStatus.ANALYZED)
        assertThat(context.sentiment!!.sentiment).isCloseTo(1.0, within(1e-9))
        assertThat(context.sentiment!!.sentimentLabel).isEqualTo(SentimentLabel.VERY_POSITIVE)
        assertThat(context.sentiment!!.analysisTier).isEqualTo(AnalysisTier.TIER_1)
        // Tier 1 mapper stamps the active version from ConnotationAnalysisConfigurationProperties (default "v1").
        assertThat(context.sentiment!!.analysisVersion).isEqualTo("v1")

        // 7. Verify the persisted envelope shape via the repository (source of truth).
        val persisted = entityConnotationRepository.findByEntityId(entityId)
        assertThat(persisted).isNotNull
        val envelope = persisted!!.connotationMetadata
        assertThat(envelope.envelopeVersion).isEqualTo("v1")

        val sentiment = envelope.axes.sentiment
        assertThat(sentiment).isNotNull
        assertThat(sentiment!!.status).isEqualTo(ConnotationStatus.ANALYZED)
        assertThat(sentiment.sentiment).isCloseTo(1.0, within(1e-9))
        assertThat(sentiment.sentimentLabel).isEqualTo(SentimentLabel.VERY_POSITIVE)
        assertThat(sentiment.analysisTier).isEqualTo(AnalysisTier.TIER_1)
        // ConnotationTier1Mapper passes themeValues through verbatim — themes is a single
        // concat string per attribute, not split into tokens.
        assertThat(sentiment.themes).containsExactly("billing,fast")

        // 8. Sibling axes still populated (Phase A behaviour preserved by the SENTIMENT path).
        assertThat(envelope.axes.relational).isNotNull
        assertThat(envelope.axes.structural).isNotNull
        assertThat(envelope.axes.structural!!.entityTypeName).isEqualTo("Review")

        // 9. Layer-4 predicate: queryable via JSONB path with UPPERCASE axis name (Task 15).
        val positiveCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*) FROM entity_connotation
            WHERE workspace_id = ?
              AND (connotation_metadata->'axes'->'SENTIMENT'->>'sentiment')::float > 0.5
            """.trimIndent(),
            Long::class.java,
            workspaceId,
        )
        assertThat(positiveCount).isEqualTo(1L)
    }

    // ------ Seeding Helpers ------

    /**
     * Creates a `manifest_catalog` row + matching `catalog_entity_types` row with
     * a TIER_1 LINEAR `connotationSignals` block. Bypasses [riven.core.service.catalog.ManifestUpsertService]
     * — the upsert path is exercised by its own unit/integration tests. Returns the manifest ID.
     */
    private fun seedCatalogManifestWithConnotationSignals(): UUID {
        val manifest = manifestCatalogRepository.saveAndFlush(
            ManifestCatalogEntity(
                key = "zendesk-fixture",
                name = "Zendesk Fixture",
                description = "Fixture manifest for ConnotationPipelineIntegrationTest.",
                manifestType = ManifestType.INTEGRATION,
                manifestVersion = "1.0",
                stale = false,
            )
        )
        val manifestId = requireNotNull(manifest.id)

        catalogEntityTypeRepository.saveAndFlush(
            CatalogEntityTypeEntity(
                manifestId = manifestId,
                key = "zendesk-review",
                displayNameSingular = "Review",
                displayNamePlural = "Reviews",
                semanticGroup = SemanticGroup.SUPPORT,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                identifierKey = "satisfaction_score",
                readonly = true,
                schema = mapOf<String, Any>(
                    "type" to "object",
                    "properties" to mapOf(
                        "satisfaction_score" to mapOf("type" to "number"),
                        "tags" to mapOf("type" to "string"),
                    ),
                ),
                connotationSignals = ConnotationSignals(
                    tier = AnalysisTier.TIER_1,
                    sentimentAttribute = "satisfaction_score",
                    sentimentScale = SentimentScale(
                        sourceMin = 1.0,
                        sourceMax = 5.0,
                        targetMin = -1.0,
                        targetMax = 1.0,
                        mappingType = ScaleMappingType.LINEAR,
                    ),
                    themeAttributes = listOf("tags"),
                ),
            )
        )

        return manifestId
    }

    /**
     * Creates the workspace entity type that points back to the catalog manifest via
     * `sourceManifestId`. The `attributeKeyMapping` is what
     * [riven.core.service.enrichment.EnrichmentService.resolveAttributeValues] uses to translate
     * manifest keys (`satisfaction_score`, `tags`) to per-workspace attribute UUIDs.
     */
    private fun seedWorkspaceEntityType(manifestId: UUID): EntityTypeEntity {
        val schema: Schema<UUID> = Schema(
            key = SchemaType.OBJECT,
            type = DataType.OBJECT,
            properties = mapOf(
                satisfactionScoreAttrId to Schema(
                    key = SchemaType.NUMBER,
                    type = DataType.NUMBER,
                    required = false,
                ),
                tagsAttrId to Schema(
                    key = SchemaType.TEXT,
                    type = DataType.STRING,
                    required = false,
                ),
            ),
        )

        return entityTypeRepository.saveAndFlush(
            EntityTypeEntity(
                key = "zendesk-review",
                displayNameSingular = "Review",
                displayNamePlural = "Reviews",
                workspaceId = workspaceId,
                identifierKey = satisfactionScoreAttrId,
                semanticGroup = SemanticGroup.SUPPORT,
                lifecycleDomain = LifecycleDomain.UNCATEGORIZED,
                sourceType = SourceType.INTEGRATION,
                sourceManifestId = manifestId,
                readonly = true,
                schema = schema,
                attributeKeyMapping = mapOf(
                    "satisfaction_score" to satisfactionScoreAttrId.toString(),
                    "tags" to tagsAttrId.toString(),
                ),
            )
        )
    }

    private fun seedEntity(typeId: UUID, typeKey: String, identifierKey: UUID): EntityEntity {
        return entityRepository.saveAndFlush(
            EntityEntity(
                workspaceId = workspaceId,
                typeId = typeId,
                typeKey = typeKey,
                identifierKey = identifierKey,
                sourceType = SourceType.INTEGRATION,
                sourceExternalId = "zendesk-review-1",
                firstSyncedAt = ZonedDateTime.now(),
                lastSyncedAt = ZonedDateTime.now(),
            )
        )
    }

    private fun seedAttribute(
        entityId: UUID,
        typeId: UUID,
        attributeId: UUID,
        schemaType: SchemaType,
        value: tools.jackson.databind.JsonNode,
    ) {
        entityAttributeRepository.saveAndFlush(
            EntityAttributeEntity(
                entityId = entityId,
                workspaceId = workspaceId,
                typeId = typeId,
                attributeId = attributeId,
                schemaType = schemaType,
                value = value,
            )
        )
    }

    private fun textValue(value: String): tools.jackson.databind.JsonNode =
        JsonNodeFactory.instance.textNode(value)

    private fun numberValue(value: Double): tools.jackson.databind.JsonNode =
        JsonNodeFactory.instance.numberNode(value)
}
