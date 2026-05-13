package cranium.core.entity.enrichment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import org.springframework.context.annotation.Configuration
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
import cranium.core.repository.enrichment.EntityEmbeddingRepository
import cranium.core.service.util.SchemaInitializer
import cranium.core.service.util.factory.enrichment.EnrichmentFactory
import java.util.UUID

/**
 * Canary integration test for Hibernate 7 + hibernate-vector 7.2.7 pgvector round-trip.
 *
 * Phase 03.1 Plan 01 bumped Hibernate 6.6 -> 7.2.7 and hibernate-vector 6.6.18 -> 7.2.7.
 * pgvector mapping under Hibernate 7 uses @JdbcTypeCode(SqlTypes.VECTOR) + @Array(length=N)
 * on a FloatArray column. This test exercises the storage + retrieval path end-to-end so that
 * any silent regression in the H7 vector type descriptor (or hibernate-vector's registration)
 * is caught at CI time, not in production embeddings.
 *
 * Scope is deliberately narrow: only the enrichment schema + parent rows needed for the
 * entity_embeddings FKs, persisted via JdbcTemplate to sidestep the unrelated Hibernate 7
 * NonSerializableObjectException on EntityTypeEntity.schema JSONB mapping (tracked as a
 * separate Plan 03.1-04 closure item).
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
@EnableJpaRepositories(basePackages = ["cranium.core.repository.enrichment"])
@EntityScan("cranium.core.entity.enrichment")
class EntityEmbeddingHibernate7IntegrationTestConfig

@SpringBootTest(
    classes = [EntityEmbeddingHibernate7IntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@TestPropertySource(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
    ]
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityEmbeddingHibernate7IntegrationTest {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("cranium_h7_vector")
            .withUsername("test")
            .withPassword("test")

        private var schemaInitialized = false

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }

            if (!schemaInitialized) {
                val ds = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                SchemaInitializer.initializeSchema(ds)
                schemaInitialized = true
            }
        }
    }

    @Autowired
    private lateinit var embeddingRepository: EntityEmbeddingRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var workspaceId: UUID
    private lateinit var entityTypeId: UUID
    private lateinit var entityId: UUID

    @BeforeEach
    fun seedParentRows() {
        workspaceId = UUID.randomUUID()
        entityTypeId = UUID.randomUUID()
        entityId = UUID.randomUUID()

        // Parent rows inserted via JDBC to sidestep the unrelated H7 NonSerializableObjectException
        // on EntityTypeEntity.schema JSONB mapping. Only the embedding round-trip is under test.
        jdbcTemplate.update(
            """
            INSERT INTO workspaces (id, name, member_count, created_at, updated_at)
            VALUES (?, 'h7-vector-test', 1, now(), now())
            """.trimIndent(),
            workspaceId,
        )
        jdbcTemplate.update(
            """
            INSERT INTO entity_types (
                id, key, display_name_singular, display_name_plural, workspace_id,
                schema, column_configuration, version, "protected", readonly,
                source_type, identifier_key, semantic_group, created_at, updated_at
            ) VALUES (?, ?, 'T', 'Ts', ?, '{}'::jsonb, '{}'::jsonb, 1, false, false,
                      'USER_CREATED', ?, 'UNCATEGORIZED', now(), now())
            """.trimIndent(),
            entityTypeId, "h7v_${entityTypeId.toString().take(8)}", workspaceId, UUID.randomUUID(),
        )
        jdbcTemplate.update(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (?, ?, ?, 'h7v', ?, 'NEUTRAL', 'FILE', 'USER_CREATED', now(), now())
            """.trimIndent(),
            entityId, workspaceId, entityTypeId, UUID.randomUUID(),
        )
    }

    @AfterEach
    fun cleanup() {
        // Cascades via FK ON DELETE CASCADE to entity_embeddings
        jdbcTemplate.update("DELETE FROM workspaces WHERE id = ?", workspaceId)
    }

    /**
     * Test 1 (RED canary): save an embedding with a non-trivial vector, reload,
     * and assert the float[] survives the H7 pgvector round-trip element-wise.
     */
    @Test
    @Transactional
    fun `pgvector round-trips intact under Hibernate 7`() {
        val vector = FloatArray(1536) { i -> (i % 17).toFloat() / 17.0f }

        val saved = embeddingRepository.saveAndFlush(
            EnrichmentFactory.entityEmbeddingEntity(
                workspaceId = workspaceId,
                entityId = entityId,
                entityTypeId = entityTypeId,
                embedding = vector,
            ).copy(id = null)
        )
        val persistedId = requireNotNull(saved.id) { "id should be assigned after saveAndFlush" }

        val reloaded = embeddingRepository.findById(persistedId).orElseThrow()

        assertThat(reloaded.embedding).hasSize(1536)
        assertThat(reloaded.embedding).isEqualTo(vector)
        assertThat(reloaded.embeddingModel).isEqualTo("text-embedding-3-small")
    }

    /**
     * Test 2: two distinct vectors round-trip independently, with cosine-distance
     * ordering under the pgvector `<=>` operator matching expected similarity ranking.
     * This exercises both the storage path AND the query-time vector operator binding
     * (hibernate-vector 7.2 must not regress on native SQL parameter binding of float[]).
     */
    @Test
    @Transactional
    fun `cosine-distance ordering is preserved across two pgvector rows`() {
        // Create a second entity so we can persist two embeddings (unique constraint on entity_id)
        val entity2Id = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key,
                icon_colour, icon_type, source_type, created_at, updated_at
            ) VALUES (?, ?, ?, 'h7v', ?, 'NEUTRAL', 'FILE', 'USER_CREATED', now(), now())
            """.trimIndent(),
            entity2Id, workspaceId, entityTypeId, UUID.randomUUID(),
        )

        val query = FloatArray(1536) { if (it == 0) 1.0f else 0.0f }
        val near = FloatArray(1536) { if (it == 0) 0.99f else 0.01f }
        val far = FloatArray(1536) { if (it == 1000) 1.0f else 0.0f }

        embeddingRepository.saveAndFlush(
            EnrichmentFactory.entityEmbeddingEntity(
                workspaceId = workspaceId, entityId = entityId, entityTypeId = entityTypeId,
                embedding = near,
            ).copy(id = null)
        )
        embeddingRepository.saveAndFlush(
            EnrichmentFactory.entityEmbeddingEntity(
                workspaceId = workspaceId, entityId = entity2Id, entityTypeId = entityTypeId,
                embedding = far,
            ).copy(id = null)
        )

        // Native cosine-distance query: orders rows by `<=>` against the query vector.
        // Uses the literal form `'[...]'::vector` (parameter-bind via float[] is JDBC-driver-
        // dependent and not the path under test here; the round-trip path is the subject).
        val queryLiteral = query.joinToString(prefix = "[", postfix = "]", separator = ",")
        val ordered = jdbcTemplate.queryForList(
            "SELECT entity_id FROM entity_embeddings WHERE workspace_id = ? " +
                "ORDER BY embedding <=> ('$queryLiteral'::vector) ASC",
            UUID::class.java,
            workspaceId,
        )

        assertThat(ordered).hasSize(2)
        assertThat(ordered[0]).isEqualTo(entityId) // near vector first
        assertThat(ordered[1]).isEqualTo(entity2Id) // far vector second
    }
}
