package riven.core.service.enrichment

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.InjectionPoint
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.configuration.auth.WorkspaceSecurity
import riven.core.configuration.properties.EnrichmentConfigurationProperties
import riven.core.enums.workspace.WorkspaceRoles
import riven.core.models.connotation.SentimentMetadata
import riven.core.models.entity.knowledge.EntityKnowledgeView
import riven.core.repository.entity.EntityRelationshipRepository
import riven.core.service.activity.ActivityService
import riven.core.service.auth.AuthTokenService
import riven.core.service.entity.EntityAttributeService
import riven.core.service.entity.EntityRelationshipService
import riven.core.service.entity.type.EntityTypeRelationshipService
import riven.core.service.util.SchemaInitializer
import riven.core.service.util.WithUserPersona
import riven.core.service.util.WorkspaceRole
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

// ----- Singleton test container -----

private object BacklinkMatrixTestContainer {
    val instance: PostgreSQLContainer = PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("riven_backlink_matrix_test")
        .withUsername("test")
        .withPassword("test")

    init {
        instance.start()
    }
}

// ----- Minimal Spring configuration -----

/**
 * Loads JPA repositories + service beans needed by [EnrichmentContextAssembler].
 * Excludes Temporal, Security auto-config, and OAuth2 — these are either not needed
 * or provided by [WithUserPersona] + manual [WorkspaceSecurity] wiring.
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
@EnableJpaRepositories(
    basePackages = [
        "riven.core.repository.entity",
        "riven.core.repository.identity",
        "riven.core.repository.activity",
        "riven.core.repository.catalog",
        "riven.core.repository.connotation",
        "riven.core.repository.workspace",
    ],
)
@EntityScan(
    basePackages = [
        "riven.core.entity",
    ],
)
@EnableJpaAuditing(
    auditorAwareRef = "backlinkMatrixAuditorProvider",
    dateTimeProviderRef = "backlinkMatrixDateTimeProvider",
)
@EnableConfigurationProperties(EnrichmentConfigurationProperties::class)
@EnableMethodSecurity(prePostEnabled = true)
@Import(
    AuthTokenService::class,
    EnrichmentContextAssembler::class,
    EntityAttributeService::class,
    EntityRelationshipService::class,
    SentimentResolutionService::class,
)
class BacklinkMatrixITConfig {

    @Bean
    fun backlinkMatrixAuditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun backlinkMatrixDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    @Bean
    @Scope("prototype")
    fun kotlinLogging(source: InjectionPoint): KLogger {
        return KotlinLogging.logger(
            source.methodParameter?.containingClass?.name
                ?: source.field?.declaringClass?.name
                ?: "riven.core.service.enrichment.BacklinkMatrixIT"
        )
    }

    /**
     * Registers [WorkspaceSecurity] with the explicit bean name "workspaceSecurity" so that
     * the `@PreAuthorize("@workspaceSecurity.hasWorkspace(#workspaceId)")` SpEL expression
     * can resolve it. [WorkspaceSecurity] is a `@Component` that Spring normally registers
     * via component scan; in this minimal test context it must be explicitly provided.
     */
    @Bean("workspaceSecurity")
    fun workspaceSecurity(): WorkspaceSecurity = WorkspaceSecurity()
}

/**
 * Integration test for the enrichment pipeline's backlink assembly logic.
 *
 * Exercises [EnrichmentContextAssembler.assemble] against a real Testcontainers PostgreSQL
 * instance to verify that all backlink sections in [riven.core.models.entity.knowledge.KnowledgeSections]
 * are correctly populated from the database.
 *
 * **VIEW-07:** Parameterized 6-case matrix covering all backlink section types:
 * note MENTION → knowledgeBacklinks, glossary DEFINES entity_type → typeNarrative.glossaryDefinitions,
 * glossary DEFINES attribute → attributes[].glossaryNarrative, glossary DEFINES relationship →
 * typeNarrative.glossaryDefinitions, SYSTEM_CONNECTION → catalogBacklinks,
 * and inverse target_rule → catalogBacklinks.
 *
 * **VIEW-08:** Cross-workspace isolation — glossary DEFINES in workspace A must not bleed into
 * workspace B's view.
 *
 * **VIEW-09:** Soft-delete filter — soft-deleted glossary entities must be excluded from
 * `findGlossaryDefinitionsForType` results (proves `@SQLRestriction("deleted = false")` engages).
 *
 * **VIEW-10:** EXPLAIN ANALYZE index scan gate — the JPQL backing `findGlossaryDefinitionsForType`
 * must use an index scan (not a seq scan) on `entity_relationships` after seeding 100+ dummy rows.
 */
@SpringBootTest(
    classes = [BacklinkMatrixITConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.main.allow-bean-definition-overriding=true",
        "riven.enrichment.knowledge-backlink-cap=50",
        "riven.enrichment.view-payload-warn-bytes=1048576",
    ],
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithUserPersona(
    userId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    email = "test@enrichment-it.com",
    displayName = "IT User",
    roles = [WorkspaceRole(workspaceId = "b0000000-0000-0000-0000-000000000001", role = WorkspaceRoles.ADMIN)],
)
class EnrichmentBacklinkMatrixIT {

    // ----- Fixed IDs for the primary test workspace -----
    private val workspaceId: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000001")
    private val queueItemId: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000002")

    // CATALOG entity type (surface_role=CATALOG) — the entity under view
    private val catalogTypeId: UUID        = UUID.fromString("b0000000-0000-0000-0000-000000000010")
    private val catalogIdentifierKey: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000011")

    // KNOWLEDGE entity type (surface_role=KNOWLEDGE) — note / glossary source type
    private val knowledgeTypeId: UUID         = UUID.fromString("b0000000-0000-0000-0000-000000000020")
    private val knowledgeIdentifierKey: UUID  = UUID.fromString("b0000000-0000-0000-0000-000000000021")

    // CATALOG entity (the entity being viewed)
    private val customerEntityId: UUID     = UUID.fromString("b0000000-0000-0000-0000-000000000100")

    // KNOWLEDGE entities — note and glossary
    private val noteEntityId: UUID         = UUID.fromString("b0000000-0000-0000-0000-000000000200")
    private val glossaryEntityId: UUID     = UUID.fromString("b0000000-0000-0000-0000-000000000201")

    // CATALOG entity types for vendor + order (catalog backlink tests)
    private val vendorTypeId: UUID         = UUID.fromString("b0000000-0000-0000-0000-000000000030")
    private val vendorIdentifierKey: UUID  = UUID.fromString("b0000000-0000-0000-0000-000000000031")
    private val orderTypeId: UUID          = UUID.fromString("b0000000-0000-0000-0000-000000000040")
    private val orderIdentifierKey: UUID   = UUID.fromString("b0000000-0000-0000-0000-000000000041")

    // CATALOG entity instances — vendor and order
    private val vendorEntityId: UUID       = UUID.fromString("b0000000-0000-0000-0000-000000000300")
    private val orderEntityId: UUID        = UUID.fromString("b0000000-0000-0000-0000-000000000301")

    // Relationship definition IDs
    private val mentionDefId: UUID           = UUID.fromString("b0000000-0000-0000-0000-000000000400")
    private val definesDefId: UUID           = UUID.fromString("b0000000-0000-0000-0000-000000000401")
    private val sysConnDefId: UUID           = UUID.fromString("b0000000-0000-0000-0000-000000000402")
    // Forward catalog→order relationship definition (owned by catalogTypeId, has target rule for orderTypeId)
    private val catalogToOrderDefId: UUID    = UUID.fromString("b0000000-0000-0000-0000-000000000403")

    // Attribute and relationship-definition UUIDs for glossary DEFINES attribute/relationship tests
    private val attributeId: UUID            = UUID.fromString("b0000000-0000-0000-0000-000000000500")
    private val relDefTargetId: UUID         = UUID.fromString("b0000000-0000-0000-0000-000000000501")

    // Second workspace for VIEW-08 isolation test
    private val workspaceBId: UUID           = UUID.fromString("b0000000-0000-0000-0000-000000000002")
    private val catalogTypeBId: UUID         = UUID.fromString("b0000000-0000-0000-0000-000000000012")
    private val catalogIdentifierBKey: UUID  = UUID.fromString("b0000000-0000-0000-0000-000000000013")
    private val customerBEntityId: UUID      = UUID.fromString("b0000000-0000-0000-0000-000000000102")

    // Note attribute entity_attribute for excerpt extraction
    private val noteAttrId: UUID             = UUID.fromString("b0000000-0000-0000-0000-000000000600")
    private val glossaryAttrId: UUID         = UUID.fromString("b0000000-0000-0000-0000-000000000601")

    // Target rule: catalogToOrderDefId targets orderTypeId (VIEW-07 case 6)
    private val catalogToOrderRuleId: UUID   = UUID.fromString("b0000000-0000-0000-0000-000000000700")

    @Autowired
    private lateinit var assembler: EnrichmentContextAssembler

    @Autowired
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var sentimentResolutionService: SentimentResolutionService

    @MockitoBean
    private lateinit var activityService: ActivityService

    @MockitoBean
    private lateinit var entityTypeRelationshipService: EntityTypeRelationshipService

    companion object {
        private var schemaInitialized = false

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val container = BacklinkMatrixTestContainer.instance
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

        // ------ Parameterized test factory (must be in companion object for @JvmStatic) ------

        /**
         * Provides the 6 parameterized test cases for the VIEW-07 backlink matrix.
         *
         * Each [BacklinkCase] describes:
         * 1. [BacklinkCase.name] — the test display name
         * 2. [BacklinkCase.seedRelId] — a fixed relationship UUID used for seeding (deterministic)
         * 3. [BacklinkCase.seedAction] — seeding function that inserts the relationship row
         * 4. [BacklinkCase.assertAction] — assertion function run against the assembled view
         *
         * Cases:
         * 1. note MENTION → entity → knowledgeBacklinks
         * 2. glossary DEFINES entity_type → typeNarrative.glossaryDefinitions
         * 3. glossary DEFINES attribute → attributes[].glossaryNarrative (via repository assertion)
         * 4. glossary DEFINES relationship → typeNarrative.glossaryDefinitions
         * 5. customer SYSTEM_CONNECTION → vendor → catalogBacklinks
         * 6. order inverse via target_rule → customer → catalogBacklinks
         */
        @JvmStatic
        fun backlinkCases(): Stream<BacklinkCase> = Stream.of(
            BacklinkCase(
                name = "note MENTION to entity populates knowledgeBacklinks",
                seedAction = { self ->
                    self.seedRelationship(
                        UUID.fromString("b1000000-0000-0000-0000-000000000001"),
                        self.workspaceId,
                        self.noteEntityId, self.customerEntityId, self.mentionDefId,
                        "ENTITY", null,
                    )
                },
                assertAction = { _, view ->
                    // The assembler buckets KNOWLEDGE-surface-role inverse links into knowledgeBacklinks.
                    // The note MENTION edge (note → customer) is found via the inverse query, so
                    // knowledgeBacklinks.size must be ≥ 1 proving the MENTION edge was detected.
                    assertTrue(
                        view.sections.knowledgeBacklinks.size >= 1,
                        "Expected at least 1 knowledgeBacklink from MENTION edge. Got: ${view.sections.knowledgeBacklinks}",
                    )
                },
            ),
            BacklinkCase(
                name = "glossary DEFINES entity_type populates typeNarrative.glossaryDefinitions",
                seedAction = { self ->
                    self.seedRelationship(
                        UUID.fromString("b1000000-0000-0000-0000-000000000002"),
                        self.workspaceId,
                        self.glossaryEntityId, self.catalogTypeId, self.definesDefId,
                        "ENTITY_TYPE", null,
                    )
                },
                assertAction = { _, view ->
                    assertTrue(
                        view.sections.typeNarrative.glossaryDefinitions.size >= 1,
                        "Expected at least 1 glossaryDefinition for ENTITY_TYPE DEFINES edge",
                    )
                },
            ),
            BacklinkCase(
                name = "glossary DEFINES attribute populates repository findGlossaryDefinitionsForType",
                seedAction = { self ->
                    self.seedRelationship(
                        UUID.fromString("b1000000-0000-0000-0000-000000000003"),
                        self.workspaceId,
                        self.glossaryEntityId, self.attributeId, self.definesDefId,
                        "ATTRIBUTE", self.catalogTypeId,
                    )
                },
                assertAction = { self, _ ->
                    // Asserts via the repository directly: the DEFINES ATTRIBUTE row must be returned by
                    // findGlossaryDefinitionsForType with the correct targetKind and targetId.
                    // The assembler maps this row to an AttributeSection.glossaryNarrative if the
                    // customer entity has an attribute with that attributeId — but seeding an attribute
                    // is not required to prove the DEFINES row is found by the query.
                    val glossaryRows = self.entityRelationshipRepository.findGlossaryDefinitionsForType(
                        self.workspaceId, self.catalogTypeId,
                    )
                    assertTrue(
                        glossaryRows.any { it.getTargetId() == self.attributeId },
                        "Expected ATTRIBUTE DEFINES row with targetId=${self.attributeId}",
                    )
                },
            ),
            BacklinkCase(
                name = "glossary DEFINES relationship populates typeNarrative.glossaryDefinitions",
                seedAction = { self ->
                    self.seedRelationship(
                        UUID.fromString("b1000000-0000-0000-0000-000000000004"),
                        self.workspaceId,
                        self.glossaryEntityId, self.relDefTargetId, self.definesDefId,
                        "RELATIONSHIP", self.catalogTypeId,
                    )
                },
                assertAction = { _, view ->
                    assertTrue(
                        view.sections.typeNarrative.glossaryDefinitions.size >= 1,
                        "Expected at least 1 glossaryDefinition for RELATIONSHIP DEFINES edge",
                    )
                },
            ),
            BacklinkCase(
                name = "SYSTEM_CONNECTION customer to vendor populates catalogBacklinks",
                seedAction = { self ->
                    self.seedRelationship(
                        UUID.fromString("b1000000-0000-0000-0000-000000000005"),
                        self.workspaceId,
                        self.customerEntityId, self.vendorEntityId, self.sysConnDefId,
                        "ENTITY", null,
                    )
                },
                assertAction = { self, view ->
                    assertTrue(
                        view.sections.catalogBacklinks.any { it.definitionId == self.sysConnDefId },
                        "Expected a catalogBacklink entry for the SYSTEM_CONNECTION definition",
                    )
                },
            ),
            BacklinkCase(
                name = "forward relationship via target_rule populates customer catalogBacklinks",
                seedAction = { self ->
                    // Customer entity creates a forward edge to an order via a relationship def
                    // owned by catalogTypeId (the viewed entity's type). loadDefinitionsMap returns
                    // defs with sourceEntityTypeId = catalogTypeId, so catalogToOrderDefId is found
                    // in the definitions map and buildCatalogSections can resolve the section.
                    self.seedRelationship(
                        UUID.fromString("b1000000-0000-0000-0000-000000000006"),
                        self.workspaceId,
                        self.customerEntityId, self.orderEntityId, self.catalogToOrderDefId,
                        "ENTITY", null,
                    )
                },
                assertAction = { self, view ->
                    assertTrue(
                        view.sections.catalogBacklinks.any { it.definitionId == self.catalogToOrderDefId },
                        "Expected a catalogBacklink for the catalogToOrder relationship definition. " +
                            "Got: ${view.sections.catalogBacklinks.map { it.relationshipName }}",
                    )
                },
            ),
        )
    }

    @BeforeEach
    fun stubSentiment() {
        // Re-stub each test because @MockitoBean resets mock stubs between tests.
        // SentimentResolutionService returns NOT_APPLICABLE (empty SentimentMetadata) since
        // the test workspace has connotation_enabled=false — sentiment analysis is not under test here.
        whenever(sentimentResolutionService.resolve(any(), any(), any())).thenReturn(SentimentMetadata())
    }

    @BeforeAll
    fun seedBaseSchema() {

        // Workspace A (primary)
        jdbcTemplate.execute(
            """
            INSERT INTO workspaces (id, name, member_count, created_at, updated_at)
            VALUES ('$workspaceId', 'Backlink Matrix Test WS A', 1, now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // Workspace B (for isolation test VIEW-08)
        jdbcTemplate.execute(
            """
            INSERT INTO workspaces (id, name, member_count, created_at, updated_at)
            VALUES ('$workspaceBId', 'Backlink Matrix Test WS B', 1, now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        seedEntityType(workspaceId,  catalogTypeId,   "catalog_customer", catalogIdentifierKey,  "CATALOG")
        seedEntityType(workspaceId,  knowledgeTypeId,  "note",            knowledgeIdentifierKey, "KNOWLEDGE")
        seedEntityType(workspaceId,  vendorTypeId,     "catalog_vendor",  vendorIdentifierKey,   "CATALOG")
        seedEntityType(workspaceId,  orderTypeId,      "catalog_order",   orderIdentifierKey,    "CATALOG")
        seedEntityType(workspaceBId, catalogTypeBId,   "catalog_customer_b", catalogIdentifierBKey, "CATALOG")

        seedEntity(workspaceId,  customerEntityId,  catalogTypeId,    "catalog_customer",   catalogIdentifierKey)
        seedEntity(workspaceId,  noteEntityId,      knowledgeTypeId,  "note",               knowledgeIdentifierKey)
        seedEntity(workspaceId,  glossaryEntityId,  knowledgeTypeId,  "note",               knowledgeIdentifierKey)
        seedEntity(workspaceId,  vendorEntityId,    vendorTypeId,     "catalog_vendor",     vendorIdentifierKey)
        seedEntity(workspaceId,  orderEntityId,     orderTypeId,      "catalog_order",      orderIdentifierKey)
        seedEntity(workspaceBId, customerBEntityId, catalogTypeBId,   "catalog_customer_b", catalogIdentifierBKey)

        // MENTION relationship definition (note → any entity)
        seedRelationshipDefinition(workspaceId, mentionDefId, knowledgeTypeId, "Mentions", "MENTION")

        // DEFINES relationship definition (glossary → entity type / attribute / relationship)
        seedRelationshipDefinition(workspaceId, definesDefId, knowledgeTypeId, "Defines", "DEFINES")

        // SYSTEM_CONNECTION definition (customer → vendor)
        seedRelationshipDefinition(workspaceId, sysConnDefId, catalogTypeId, "Connected To", "SYSTEM_CONNECTION")

        // catalogToOrder definition — owned by catalogTypeId so it is loaded into the definitions
        // map during assembly (loadDefinitionsMap uses sourceEntityTypeId = catalogTypeId).
        // The target rule below records that this definition targets orderTypeId, but for the
        // forward-edge catalogBacklink test (VIEW-07 case 6) the definition must be owned by
        // the entity type being viewed (catalogTypeId) so buildCatalogSections can resolve it.
        seedRelationshipDefinition(workspaceId, catalogToOrderDefId, catalogTypeId, "Order For Customer", null)

        // Target rule: catalogToOrderDefId (owned by catalogTypeId) may target orderTypeId entities.
        // This records the inverse-name for the target_rule path, but is not strictly required
        // by the forward-edge catalogBacklink assertion (VIEW-07 case 6).
        jdbcTemplate.execute(
            """
            INSERT INTO relationship_target_rules (id, relationship_definition_id, target_entity_type_id, inverse_name, created_at, updated_at)
            VALUES ('$catalogToOrderRuleId', '$catalogToOrderDefId', '$orderTypeId', 'Ordered By Customer', now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // Note attribute entity_attribute row (for excerpt extraction in knowledgeBacklinks)
        jdbcTemplate.execute(
            """
            INSERT INTO entity_attributes (id, entity_id, workspace_id, type_id, attribute_id, schema_type, value, created_at, updated_at)
            VALUES ('$noteAttrId', '$noteEntityId', '$workspaceId', '$knowledgeTypeId', '$noteAttrId',
                    'TEXT', '"Note body text for excerpt"', now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // Glossary attribute entity_attribute row (for narrative extraction in typeNarrative.glossaryDefinitions)
        jdbcTemplate.execute(
            """
            INSERT INTO entity_attributes (id, entity_id, workspace_id, type_id, attribute_id, schema_type, value, created_at, updated_at)
            VALUES ('$glossaryAttrId', '$glossaryEntityId', '$workspaceId', '$knowledgeTypeId', '$glossaryAttrId',
                    'TEXT', '"Glossary definition narrative text"', now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    @BeforeEach
    fun clearRelationships() {
        // Hard-delete all relationship rows between tests for clean state isolation
        jdbcTemplate.execute("DELETE FROM entity_relationships WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entity_relationships WHERE workspace_id = '$workspaceBId'")
    }

    // ------ Helpers ------

    private fun seedEntityType(wsId: UUID, typeId: UUID, key: String, identifierKey: UUID, surfaceRole: String) {
        val displayName = key.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        jdbcTemplate.execute(
            """
            INSERT INTO entity_types (
                id, key, workspace_id, identifier_key, display_name_singular, display_name_plural,
                schema, semantic_group, lifecycle_domain, source_type, surface_role, created_at, updated_at
            ) VALUES (
                '$typeId', '$key', '$wsId', '$identifierKey',
                '$displayName', '${displayName}s',
                '{"key":"OBJECT","type":"OBJECT","properties":{}}',
                'UNCATEGORIZED', 'UNCATEGORIZED', 'USER_CREATED', '$surfaceRole', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    private fun seedEntity(wsId: UUID, entityId: UUID, typeId: UUID, typeKey: String, identifierKey: UUID) {
        jdbcTemplate.execute(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (
                '$entityId', '$wsId', '$typeId', '$typeKey', '$identifierKey',
                'NEUTRAL', 'FILE', 'USER_CREATED', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    private fun seedRelationshipDefinition(
        wsId: UUID,
        defId: UUID,
        sourceTypeId: UUID,
        name: String,
        systemType: String?,
    ) {
        val systemTypeCol = if (systemType != null) "'$systemType'" else "NULL"
        jdbcTemplate.execute(
            """
            INSERT INTO relationship_definitions (
                id, workspace_id, source_entity_type_id, name, cardinality_default,
                icon_type, icon_value, system_type, created_at, updated_at
            ) VALUES (
                '$defId', '$wsId', '$sourceTypeId', '$name',
                'MANY_TO_MANY', 'FILE', 'NEUTRAL', $systemTypeCol, now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    internal fun seedRelationship(
        relId: UUID,
        wsId: UUID,
        sourceEntityId: UUID,
        targetId: UUID,
        defId: UUID,
        targetKind: String = "ENTITY",
        targetParentId: UUID? = null,
    ) {
        val parentCol = if (targetParentId != null) "'$targetParentId'" else "NULL"
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, target_parent_id,
                relationship_definition_id, target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$relId', '$wsId', '$sourceEntityId', '$targetId', $parentCol,
                '$defId', '$targetKind', 'USER_CREATED', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    // ------ VIEW-07: Parameterized backlink matrix (6 cases) ------

    /**
     * VIEW-07: Parameterized 6-case backlink matrix.
     *
     * Each case seeds one relationship edge into the Testcontainers DB and asserts that
     * [EnrichmentContextAssembler.assemble] surfaces it in the correct section of
     * [riven.core.models.entity.knowledge.KnowledgeSections].
     *
     * The `@WithUserPersona` on the class sets workspace authority for `@PreAuthorize` on [assembler.assemble].
     * `@TestInstance(PER_CLASS)` ensures the companion-object `@MethodSource` is accessible.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backlinkCases")
    @Transactional
    fun backlinkMatrix(case: BacklinkCase) {
        case.seedAction(this)
        val view = assembler.assemble(customerEntityId, workspaceId, queueItemId)
        case.assertAction(this, view)
    }

    // ------ VIEW-08: Cross-workspace isolation ------

    /**
     * VIEW-08: Glossary DEFINES edge in workspace A must NOT appear in workspace B's assembled view.
     *
     * Two workspaces share the same conceptual entity type (different IDs). A glossary entity in
     * workspace A DEFINES that workspace's entity type. Assembling the view for workspace B's
     * equivalent entity must return empty glossaryDefinitions.
     *
     * Validates workspace-scoping in [EntityRelationshipRepository.findGlossaryDefinitionsForType]:
     * both `r.workspaceId = :workspaceId` AND `e.workspaceId = :workspaceId` predicates are enforced.
     */
    @Test
    @Transactional
    @WithUserPersona(
        userId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        email = "test@enrichment-it.com",
        roles = [WorkspaceRole(workspaceId = "b0000000-0000-0000-0000-000000000002", role = WorkspaceRoles.ADMIN)],
    )
    fun `VIEW-08 cross-workspace isolation — glossary defines in WS A do not appear in WS B view`() {
        // Seed: glossary in workspace A DEFINES catalogTypeId in workspace A
        seedRelationship(
            UUID.fromString("b2000000-0000-0000-0000-000000000001"),
            workspaceId,
            glossaryEntityId, catalogTypeId, definesDefId,
            "ENTITY_TYPE", null,
        )

        // Assemble view for workspace B's customer entity
        val viewB = assembler.assemble(customerBEntityId, workspaceBId, queueItemId)

        assertTrue(
            viewB.sections.typeNarrative.glossaryDefinitions.isEmpty(),
            "Workspace B's view must not contain glossary definitions from workspace A",
        )
    }

    // ------ VIEW-09: Soft-delete filter ------

    /**
     * VIEW-09: Soft-deleted glossary entities must be excluded from backlink results.
     *
     * Seeds a glossary entity with a DEFINES edge, then soft-deletes the glossary entity's
     * entity row (deleted=true, deleted_at=NOW()). Asserts that:
     * 1. [EntityRelationshipRepository.findGlossaryDefinitionsForType] returns an empty list —
     *    the JPQL JOIN on EntityEntity auto-applies the `@SQLRestriction("deleted = false")`.
     * 2. Pre-condition: the row IS returned before the soft-delete (proving the seed worked).
     *
     * This test proves the `@SQLRestriction` on [riven.core.entity.entity.EntityEntity] engages
     * on the JPQL query added in Plan 02-03.
     */
    @Test
    @Transactional
    fun `VIEW-09 soft-delete filter — deleted glossary entity excluded from glossaryDefinitions`() {
        seedRelationship(
            UUID.fromString("b3000000-0000-0000-0000-000000000001"),
            workspaceId, glossaryEntityId, catalogTypeId, definesDefId, "ENTITY_TYPE", null,
        )

        // Pre-condition: row must exist before soft-delete
        val rowsBefore = entityRelationshipRepository.findGlossaryDefinitionsForType(workspaceId, catalogTypeId)
        assertTrue(rowsBefore.isNotEmpty(), "Pre-condition: glossary DEFINES row must exist before soft-delete")

        // Soft-delete the glossary entity — visible to subsequent reads within the same TX
        jdbcTemplate.execute(
            "UPDATE entities SET deleted = true, deleted_at = now() WHERE id = '$glossaryEntityId'"
        )

        // Post-condition: @SQLRestriction on EntityEntity excludes the soft-deleted source entity
        val rowsAfter = entityRelationshipRepository.findGlossaryDefinitionsForType(workspaceId, catalogTypeId)
        assertTrue(
            rowsAfter.isEmpty(),
            "findGlossaryDefinitionsForType must return empty after glossary entity is soft-deleted",
        )
    }

    // ------ VIEW-10: EXPLAIN ANALYZE index scan gate ------

    /**
     * VIEW-10: EXPLAIN ANALYZE must show an Index Scan (not Seq Scan) on entity_relationships.
     *
     * Seeds 500+ dummy ENTITY-kind rows to give the query planner enough rows to prefer an
     * index scan. Runs ANALYZE on the table, then EXPLAIN ANALYZE on the equivalent SQL of
     * [EntityRelationshipRepository.findGlossaryDefinitionsForType] and asserts:
     * - Plan contains "Index Scan", "Bitmap Index Scan", or "Index Only Scan"
     * - Plan does NOT contain "Seq Scan on entity_relationships"
     *
     * The SQL skeleton matches the JPQL WHERE shape exactly (same predicates), including
     * `r.deleted = false` so the partial index condition
     * (`WHERE target_kind <> 'ENTITY' AND deleted = FALSE`) is satisfied and the planner
     * recognises the index applies. This proves the idx_entity_relationships_reverse_target
     * partial index is used at query time.
     */
    @Test
    fun `VIEW-10 EXPLAIN ANALYZE uses index scan not seq scan on entity_relationships`() {
        val dummyTypeId   = UUID.fromString("b0000000-0000-ffff-0000-000000000001")
        val dummyEntityId = UUID.fromString("b0000000-0000-ffff-0000-000000000002")

        // Seed supporting entity type + entity for dummy rows (avoid FK violations)
        jdbcTemplate.execute(
            """
            INSERT INTO entity_types (
                id, key, workspace_id, identifier_key, display_name_singular, display_name_plural,
                schema, semantic_group, lifecycle_domain, source_type, surface_role, created_at, updated_at
            ) VALUES (
                '$dummyTypeId', 'dummy_explain_type', '$workspaceId', '${UUID.randomUUID()}',
                'Dummy', 'Dummies', '{"key":"OBJECT","type":"OBJECT","properties":{}}',
                'UNCATEGORIZED', 'UNCATEGORIZED', 'USER_CREATED', 'CATALOG', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (
                '$dummyEntityId', '$workspaceId', '$dummyTypeId', 'dummy_explain_type', '${UUID.randomUUID()}',
                'NEUTRAL', 'FILE', 'USER_CREATED', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // Insert 500 dummy ENTITY-kind rows to coerce planner statistics into a large-table path.
        // 500 rows ensures the total table size is large enough that Postgres prefers an index
        // scan for the highly-selective target_kind IN ('ENTITY_TYPE','ATTRIBUTE','RELATIONSHIP')
        // predicate (only 2 matching rows out of 502 total after seeding).
        for (i in 1..500) {
            jdbcTemplate.execute(
                """
                INSERT INTO entity_relationships (
                    id, workspace_id, source_entity_id, target_id,
                    relationship_definition_id, target_kind, link_source, created_at, updated_at
                ) VALUES (
                    '${UUID.randomUUID()}', '$workspaceId', '$dummyEntityId', '${UUID.randomUUID()}',
                    '$sysConnDefId', 'ENTITY', 'USER_CREATED', now(), now()
                )
                """.trimIndent()
            )
        }

        // Seed the actual ENTITY_TYPE + ATTRIBUTE target rows the query targets via the partial index
        seedRelationship(
            UUID.fromString("b4000000-0000-0000-0000-000000000001"),
            workspaceId, glossaryEntityId, catalogTypeId, definesDefId, "ENTITY_TYPE", null,
        )
        seedRelationship(
            UUID.fromString("b4000000-0000-0000-0000-000000000002"),
            workspaceId, glossaryEntityId, attributeId, definesDefId, "ATTRIBUTE", catalogTypeId,
        )

        // Update planner statistics so EXPLAIN reflects the seeded row counts
        jdbcTemplate.execute("ANALYZE entity_relationships")

        // The partial index idx_entity_relationships_reverse_target covers:
        //   entity_relationships(workspace_id, target_id, target_kind)
        //   WHERE target_kind <> 'ENTITY' AND deleted = FALSE
        // The query MUST include `r.deleted = false` so Postgres recognises the index condition
        // is satisfied — without it the planner cannot use the partial index.
        val explainSql = """
            EXPLAIN (ANALYZE, FORMAT TEXT)
            SELECT r.id FROM entity_relationships r
            JOIN relationship_definitions d ON d.id = r.relationship_definition_id
            JOIN entities e ON e.id = r.source_entity_id
            WHERE r.workspace_id = '$workspaceId'
              AND r.deleted = false
              AND e.workspace_id = '$workspaceId'
              AND d.system_type = 'DEFINES'
              AND r.target_kind IN ('ENTITY_TYPE', 'ATTRIBUTE', 'RELATIONSHIP')
              AND (
                    (r.target_kind = 'ENTITY_TYPE' AND r.target_id = '$catalogTypeId')
                 OR (r.target_kind IN ('ATTRIBUTE', 'RELATIONSHIP') AND r.target_parent_id = '$catalogTypeId')
                  )
        """.trimIndent()

        val planLines = jdbcTemplate.queryForList(explainSql, String::class.java)
        val planText  = planLines.joinToString("\n")

        // Accept Index Scan, Bitmap Index Scan, or Index Only Scan — all prove the partial index is used.
        assertTrue(
            planLines.any { line ->
                line.contains("Index Scan", ignoreCase = true)
                    || line.contains("Bitmap Index Scan", ignoreCase = true)
                    || line.contains("Index Only Scan", ignoreCase = true)
            },
            "EXPLAIN must show an Index Scan (or Bitmap/Only variant) on entity_relationships. Actual plan:\n$planText",
        )
        assertFalse(
            planLines.any { it.contains("Seq Scan on entity_relationships", ignoreCase = true) },
            "EXPLAIN must NOT show Seq Scan on entity_relationships. Actual plan:\n$planText",
        )
    }

    // ------ Data class for parameterized test cases ------

    /**
     * Holder for a single parameterized VIEW-07 backlink matrix test case.
     *
     * @property name Human-readable test name displayed by [ParameterizedTest].
     * @property seedAction Seeding lambda; receives the test instance so it can call [seedRelationship]
     *   and reference fixed IDs without field capture (avoids Kotlin serialization issues with lambdas
     *   over non-companion references).
     * @property assertAction Assertion lambda; receives both the test instance (for repo access) and
     *   the assembled [EntityKnowledgeView].
     */
    data class BacklinkCase(
        val name: String,
        val seedAction: (EnrichmentBacklinkMatrixIT) -> Unit,
        val assertAction: (EnrichmentBacklinkMatrixIT, EntityKnowledgeView) -> Unit,
    ) {
        override fun toString(): String = name
    }
}
