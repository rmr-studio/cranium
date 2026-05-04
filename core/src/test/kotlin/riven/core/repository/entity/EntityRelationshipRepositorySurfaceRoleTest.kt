package riven.core.repository.entity

import io.github.oshai.kotlinlogging.KLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.enums.entity.EntityTypeRole
import riven.core.enums.entity.RelationshipTargetKind
import riven.core.projection.entity.toEntityLink
import riven.core.service.util.SchemaInitializer
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Singleton Postgres container for the surface-role repository integration tests.
 */
private object SurfaceRoleTestContainer {
    val instance: PostgreSQLContainer = PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("riven_surface_role_test")
        .withUsername("test")
        .withPassword("test")

    init {
        instance.start()
    }
}

/**
 * Minimal Spring config for the surface-role repository integration test.
 * Loads only the JPA/repository layer — no services, no security, no Temporal.
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
    ]
)
@EntityScan(
    basePackages = [
        "riven.core.entity.entity",
    ]
)
@EnableJpaAuditing(
    auditorAwareRef = "srAuditorProvider",
    dateTimeProviderRef = "srDateTimeProvider",
)
class SurfaceRoleTestConfig {

    @Bean
    fun srAuditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun srDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    @Bean
    fun klogger(): KLogger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
}

/**
 * VIEW-02 + VIEW-03 integration tests for [EntityRelationshipRepository].
 *
 * **VIEW-02:** findGlossaryDefinitionsForType returns rows for all three target_kind variants
 *   (ENTITY_TYPE, ATTRIBUTE, RELATIONSHIP) via the new JPQL query.
 *
 * **VIEW-03:** sourceSurfaceRole is projected in all 4 native JPQL queries (findEntityLinksBySourceId,
 *   findEntityLinksBySourceIdIn, findInverseEntityLinksByTargetId, findInverseEntityLinksByTargetIdIn).
 *
 * Tests use JDBC directly to seed data (bypassing soft-delete listeners) so the JPA @SQLRestriction
 * filter ("deleted = false") is satisfied without needing full Spring entity wiring.
 */
@SpringBootTest(
    classes = [SurfaceRoleTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.main.allow-bean-definition-overriding=true",
    ]
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class EntityRelationshipRepositorySurfaceRoleTest {

    @Autowired
    private lateinit var entityRelationshipRepository: EntityRelationshipRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    // Fixed workspace for all tests in this class
    private val workspaceId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000100")

    // CATALOG entity type (role=CATALOG) — target entity type for glossary DEFINES
    private val catalogTypeId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000101")
    private val catalogIdentifierKey: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000102")

    // KNOWLEDGE entity type (role=KNOWLEDGE) — type for glossary / note entities
    private val knowledgeTypeId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000103")
    private val knowledgeIdentifierKey: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000104")

    // DEFINES relationship definition
    private val definesDefinitionId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000200")

    // CATALOG entity (the viewed entity — target of glossary DEFINES)
    private val catalogEntityId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000300")

    // KNOWLEDGE entity (the glossary entity — source of DEFINES edges)
    private val glossaryEntityId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000301")

    // Attribute and relationship IDs for VIEW-02 ATTRIBUTE/RELATIONSHIP coverage
    private val attributeId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000400")
    private val relDefId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000401")

    // Forward-link relationship definition (non-DEFINES)
    private val forwardRelDefId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000500")

    companion object {
        private var schemaInitialized = false

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val container = SurfaceRoleTestContainer.instance
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
    fun seedBaseData() {
        // Workspace
        jdbcTemplate.execute(
            """
            INSERT INTO workspaces (id, name, member_count, created_at, updated_at)
            VALUES ('$workspaceId', 'Surface Role Test Workspace', 1, now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // CATALOG entity type
        jdbcTemplate.execute(
            """
            INSERT INTO entity_types (
                id, key, workspace_id, identifier_key, display_name_singular, display_name_plural,
                schema, semantic_group, lifecycle_domain, source_type, surface_role
            ) VALUES (
                '$catalogTypeId', 'catalog_type', '$workspaceId', '$catalogIdentifierKey',
                'Catalog Entity', 'Catalog Entities',
                '{"key":"OBJECT","type":"OBJECT","properties":{}}',
                'UNCATEGORIZED', 'UNCATEGORIZED', 'USER_CREATED', 'CATALOG'
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // KNOWLEDGE entity type
        jdbcTemplate.execute(
            """
            INSERT INTO entity_types (
                id, key, workspace_id, identifier_key, display_name_singular, display_name_plural,
                schema, semantic_group, lifecycle_domain, source_type, surface_role
            ) VALUES (
                '$knowledgeTypeId', 'knowledge_type', '$workspaceId', '$knowledgeIdentifierKey',
                'Knowledge Entity', 'Knowledge Entities',
                '{"key":"OBJECT","type":"OBJECT","properties":{}}',
                'UNCATEGORIZED', 'UNCATEGORIZED', 'USER_CREATED', 'KNOWLEDGE'
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // DEFINES relationship definition (system_type=DEFINES, source=knowledgeTypeId)
        jdbcTemplate.execute(
            """
            INSERT INTO relationship_definitions (
                id, workspace_id, source_entity_type_id, name, cardinality_default,
                icon_type, icon_value, system_type, created_at, updated_at
            ) VALUES (
                '$definesDefinitionId', '$workspaceId', '$knowledgeTypeId',
                'Defines', 'MANY_TO_MANY', 'FILE', 'File', 'DEFINES', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // Forward relationship definition (non-DEFINES, CATALOG → CATALOG)
        jdbcTemplate.execute(
            """
            INSERT INTO relationship_definitions (
                id, workspace_id, source_entity_type_id, name, cardinality_default,
                icon_type, icon_value, created_at, updated_at
            ) VALUES (
                '$forwardRelDefId', '$workspaceId', '$catalogTypeId',
                'Related', 'MANY_TO_MANY', 'FILE', 'File', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // CATALOG entity (viewed entity)
        jdbcTemplate.execute(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (
                '$catalogEntityId', '$workspaceId', '$catalogTypeId', 'catalog_type', '$catalogIdentifierKey',
                'NEUTRAL', 'FILE', 'USER_CREATED', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )

        // KNOWLEDGE entity (glossary entity)
        jdbcTemplate.execute(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (
                '$glossaryEntityId', '$workspaceId', '$knowledgeTypeId', 'knowledge_type', '$knowledgeIdentifierKey',
                'NEUTRAL', 'FILE', 'USER_CREATED', now(), now()
            ) ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    @BeforeEach
    fun clearRelationships() {
        // Hard-delete all relationships to ensure test isolation
        jdbcTemplate.execute("DELETE FROM entity_relationships WHERE workspace_id = '$workspaceId'")
    }

    // ------ VIEW-02: findGlossaryDefinitionsForType ------

    /**
     * Test 1 (VIEW-02 ENTITY_TYPE): glossary DEFINES an entity type via target_kind=ENTITY_TYPE.
     * The row is returned with the correct targetKind, sourceEntityId, and empty narrative.
     */
    @Test
    fun `findGlossaryDefinitionsForType returns ENTITY_TYPE row when glossary DEFINES the entity type`() {
        val relId = UUID.randomUUID()
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, relationship_definition_id,
                target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$relId', '$workspaceId', '$glossaryEntityId', '$catalogTypeId',
                '$definesDefinitionId', 'ENTITY_TYPE', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )

        val rows = entityRelationshipRepository.findGlossaryDefinitionsForType(workspaceId, catalogTypeId)

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(RelationshipTargetKind.ENTITY_TYPE, row.getTargetKind())
        assertEquals(glossaryEntityId, row.getSourceEntityId())
        assertEquals(catalogTypeId, row.getTargetId())
        assertEquals("", row.getNarrative(), "Narrative must be empty string (Kotlin layer fills it)")
        assertNotNull(row.getCreatedAt())
    }

    /**
     * Test 2 (VIEW-02 ATTRIBUTE): glossary DEFINES an attribute via target_kind=ATTRIBUTE + target_parent_id.
     * The row is returned with targetKind=ATTRIBUTE and targetId=attributeId.
     */
    @Test
    fun `findGlossaryDefinitionsForType returns ATTRIBUTE row when glossary DEFINES an attribute on the entity type`() {
        val relId = UUID.randomUUID()
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, target_parent_id,
                relationship_definition_id, target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$relId', '$workspaceId', '$glossaryEntityId', '$attributeId', '$catalogTypeId',
                '$definesDefinitionId', 'ATTRIBUTE', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )

        val rows = entityRelationshipRepository.findGlossaryDefinitionsForType(workspaceId, catalogTypeId)

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(RelationshipTargetKind.ATTRIBUTE, row.getTargetKind())
        assertEquals(attributeId, row.getTargetId())
        assertEquals(glossaryEntityId, row.getSourceEntityId())
    }

    /**
     * Test 3 (VIEW-02 RELATIONSHIP): glossary DEFINES a relationship definition via
     * target_kind=RELATIONSHIP + target_parent_id. The row is returned with targetKind=RELATIONSHIP.
     */
    @Test
    fun `findGlossaryDefinitionsForType returns RELATIONSHIP row when glossary DEFINES a relationship on the entity type`() {
        val relId = UUID.randomUUID()
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, target_parent_id,
                relationship_definition_id, target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$relId', '$workspaceId', '$glossaryEntityId', '$relDefId', '$catalogTypeId',
                '$definesDefinitionId', 'RELATIONSHIP', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )

        val rows = entityRelationshipRepository.findGlossaryDefinitionsForType(workspaceId, catalogTypeId)

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(RelationshipTargetKind.RELATIONSHIP, row.getTargetKind())
        assertEquals(relDefId, row.getTargetId())
    }

    // ------ VIEW-03: sourceSurfaceRole projection ------

    /**
     * Test 4 (VIEW-03 forward): findEntityLinksBySourceId projects sourceSurfaceRole.
     * A forward edge from a CATALOG source entity → a CATALOG target entity should project CATALOG.
     */
    @Test
    fun `findEntityLinksBySourceId projects sourceSurfaceRole as CATALOG for CATALOG source entity`() {
        val relId = UUID.randomUUID()
        // Forward edge: catalogEntity → catalogEntity (same entity for simplicity, self-loop)
        // Use a second CATALOG entity as target to keep semantics clear
        val targetEntityId = UUID.randomUUID()
        jdbcTemplate.execute(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (
                '$targetEntityId', '$workspaceId', '$catalogTypeId', 'catalog_type', '$catalogIdentifierKey',
                'NEUTRAL', 'FILE', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, relationship_definition_id,
                target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$relId', '$workspaceId', '$catalogEntityId', '$targetEntityId',
                '$forwardRelDefId', 'ENTITY', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )

        val links = entityRelationshipRepository.findEntityLinksBySourceId(catalogEntityId, workspaceId)

        assertEquals(1, links.size)
        val entityLink = links.first().toEntityLink()
        assertEquals(EntityTypeRole.CATALOG, entityLink.sourceSurfaceRole)
    }

    /**
     * Test 5 (VIEW-03 inverse): findInverseEntityLinksByTargetId projects sourceSurfaceRole.
     * A KNOWLEDGE entity referencing the CATALOG entity via DEFINES should project KNOWLEDGE.
     */
    @Test
    fun `findInverseEntityLinksByTargetId projects sourceSurfaceRole as KNOWLEDGE for KNOWLEDGE source entity`() {
        val relId = UUID.randomUUID()
        // Inverse edge: KNOWLEDGE source → CATALOG target (the viewed entity)
        // This simulates a knowledge entity (note/glossary) referencing a catalog entity
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, relationship_definition_id,
                target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$relId', '$workspaceId', '$glossaryEntityId', '$catalogEntityId',
                '$definesDefinitionId', 'ENTITY', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )

        // For inverse lookup we need a relationship_target_rule or system_type DEFINES with KNOWLEDGE role
        // The inverse query admits DEFINES + surface_role=KNOWLEDGE edges
        val links = entityRelationshipRepository.findInverseEntityLinksByTargetId(catalogEntityId, workspaceId)

        // At minimum: the projection returned (if any) has sourceSurfaceRole=KNOWLEDGE
        // The predicate: rd.system_type IN ('ATTACHMENT','MENTION','DEFINES') AND src_t.surface_role='KNOWLEDGE'
        assertTrue(links.isNotEmpty(), "Expected at least one inverse KNOWLEDGE DEFINES link")
        val entityLink = links.first().toEntityLink()
        assertEquals(EntityTypeRole.KNOWLEDGE, entityLink.sourceSurfaceRole)
    }

    /**
     * Test 6 (VIEW-03 batch parity): batch variants project same sourceSurfaceRole as single-id variants.
     * Uses the same CATALOG→CATALOG forward edge as Test 4 and verifies batch result matches single-id result.
     */
    @Test
    fun `batch variants project same sourceSurfaceRole as single-id variants`() {
        val relId = UUID.randomUUID()
        val targetEntityId = UUID.randomUUID()
        jdbcTemplate.execute(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (
                '$targetEntityId', '$workspaceId', '$catalogTypeId', 'catalog_type', '$catalogIdentifierKey',
                'NEUTRAL', 'FILE', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, relationship_definition_id,
                target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$relId', '$workspaceId', '$catalogEntityId', '$targetEntityId',
                '$forwardRelDefId', 'ENTITY', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )

        val singleResult = entityRelationshipRepository
            .findEntityLinksBySourceId(catalogEntityId, workspaceId)
            .map { it.toEntityLink().sourceSurfaceRole }

        val batchResult = entityRelationshipRepository
            .findEntityLinksBySourceIdIn(arrayOf(catalogEntityId), workspaceId)
            .map { it.toEntityLink().sourceSurfaceRole }

        assertEquals(singleResult, batchResult, "Batch forward variant must project same sourceSurfaceRole as single-id variant")

        // Also verify inverse batch parity
        val inverseRelId = UUID.randomUUID()
        jdbcTemplate.execute(
            """
            INSERT INTO entity_relationships (
                id, workspace_id, source_entity_id, target_id, relationship_definition_id,
                target_kind, link_source, created_at, updated_at
            ) VALUES (
                '$inverseRelId', '$workspaceId', '$glossaryEntityId', '$catalogEntityId',
                '$definesDefinitionId', 'ENTITY', 'USER_CREATED', now(), now()
            )
            """.trimIndent()
        )

        val singleInverseResult = entityRelationshipRepository
            .findInverseEntityLinksByTargetId(catalogEntityId, workspaceId)
            .map { it.toEntityLink().sourceSurfaceRole }

        val batchInverseResult = entityRelationshipRepository
            .findInverseEntityLinksByTargetIdIn(arrayOf(catalogEntityId), workspaceId)
            .map { it.toEntityLink().sourceSurfaceRole }

        assertEquals(singleInverseResult, batchInverseResult, "Batch inverse variant must project same sourceSurfaceRole as single-id variant")
    }
}
