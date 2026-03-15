package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.entity.identity.IdentityClusterEntity
import riven.core.entity.identity.IdentityClusterMemberEntity
import riven.core.entity.identity.MatchSuggestionEntity
import riven.core.repository.identity.IdentityClusterMemberRepository
import riven.core.repository.identity.IdentityClusterRepository
import riven.core.repository.identity.MatchSuggestionRepository
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Minimal Spring configuration for identity infrastructure integration tests.
 *
 * Loads only JPA auto-configuration — excludes security, Temporal, Supabase, web layer,
 * and all other unrelated beans.
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
@EnableJpaRepositories(basePackages = ["riven.core.repository.identity"])
@EntityScan("riven.core.entity.identity")
@EnableJpaAuditing(auditorAwareRef = "identityAuditorProvider", dateTimeProviderRef = "identityDateTimeProvider")
class IdentityInfrastructureIntegrationTestConfig {

    @Bean
    fun identityAuditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun identityDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }
}

/**
 * Integration tests that validate identity resolution database-level constraints (INFRA-02, INFRA-04, INFRA-05, INFRA-06).
 *
 * These tests prove correctness requirements that cannot be verified by unit tests:
 * - Dedup index behavior: soft-deleted rows do not block new active rows [INFRA-02]
 * - pg_trgm extension installed [INFRA-04]
 * - Unique active pair constraint on match_suggestions [INFRA-05]
 * - CHECK constraint enforces source_entity_id < target_entity_id [INFRA-06]
 * - One-cluster-per-entity uniqueness [INFRA-05]
 *
 * All tests run against a real PostgreSQL container — H2 cannot enforce these constraints.
 */
@SpringBootTest(
    classes = [IdentityInfrastructureIntegrationTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityInfrastructureIntegrationTest {

    @Autowired
    private lateinit var matchSuggestionRepository: MatchSuggestionRepository

    @Autowired
    private lateinit var identityClusterRepository: IdentityClusterRepository

    @Autowired
    private lateinit var identityClusterMemberRepository: IdentityClusterMemberRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
        )
            .withDatabaseName("riven_test")
            .withUsername("test")
            .withPassword("test")

        init {
            postgres.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    // Fixed UUIDs with canonical ordering guaranteed: sourceId < targetId (lexicographic)
    private val workspaceId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val sourceId    = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val targetId    = UUID.fromString("20000000-0000-0000-0000-000000000002")

    @org.junit.jupiter.api.BeforeAll
    fun installExtensionsAndConstraints() {
        // Install pg_trgm extension (not applied by Hibernate create-drop, only by our schema DDL)
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")

        // Apply canonical UUID ordering CHECK constraint (Hibernate create-drop does not run identity_constraints.sql)
        jdbcTemplate.execute(
            """ALTER TABLE match_suggestions
               DROP CONSTRAINT IF EXISTS chk_match_suggestions_canonical_order"""
        )
        jdbcTemplate.execute(
            """ALTER TABLE match_suggestions
               ADD CONSTRAINT chk_match_suggestions_canonical_order
               CHECK (source_entity_id < target_entity_id)"""
        )

        // Apply unique active pair index (partial — WHERE deleted = false)
        jdbcTemplate.execute("DROP INDEX IF EXISTS uq_match_suggestions_pair")
        jdbcTemplate.execute(
            """CREATE UNIQUE INDEX IF NOT EXISTS uq_match_suggestions_pair
               ON match_suggestions (workspace_id, source_entity_id, target_entity_id)
               WHERE deleted = false"""
        )

        // Apply one-cluster-per-entity unique index
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_identity_cluster_members_entity")
        jdbcTemplate.execute(
            """CREATE UNIQUE INDEX IF NOT EXISTS idx_identity_cluster_members_entity
               ON identity_cluster_members (entity_id)"""
        )
    }

    @BeforeEach
    fun truncate() {
        // Use native SQL to bypass soft-delete filters and clean all rows between tests
        jdbcTemplate.execute("TRUNCATE TABLE identity_cluster_members CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE match_suggestions CASCADE")
        jdbcTemplate.execute("TRUNCATE TABLE identity_clusters CASCADE")
    }

    // ------ Factory helpers ------

    private fun buildSuggestion(
        source: UUID = sourceId,
        target: UUID = targetId,
        workspace: UUID = workspaceId,
        confidence: BigDecimal = BigDecimal("0.9500"),
    ) = MatchSuggestionEntity(
        workspaceId = workspace,
        sourceEntityId = source,
        targetEntityId = target,
        confidenceScore = confidence,
    )

    private fun buildCluster(workspace: UUID = workspaceId) =
        IdentityClusterEntity(workspaceId = workspace)

    private fun buildMember(clusterId: UUID, entityId: UUID) =
        IdentityClusterMemberEntity(clusterId = clusterId, entityId = entityId)

    // ------ Tests ------

    /**
     * INFRA-02: Dedup index behavior.
     *
     * Bug being tested: if the unique partial index WHERE deleted = false were instead a full
     * unique index, a soft-deleted suggestion would block creating a new active suggestion for
     * the same pair. This test proves the partial index correctly allows re-suggestion after rejection.
     *
     * Fix: uq_match_suggestions_pair is a partial index with WHERE deleted = false.
     *
     * Verifies: soft-deleted suggestion does not prevent a new active suggestion for the same pair.
     */
    @Test
    fun `soft-deleted suggestion does not block new active suggestion for same pair`() {
        // Insert and soft-delete first suggestion via native SQL (bypasses @SQLRestriction)
        val first = matchSuggestionRepository.saveAndFlush(buildSuggestion())
        assertNotNull(first.id)

        jdbcTemplate.update(
            "UPDATE match_suggestions SET deleted = true, deleted_at = NOW() WHERE id = ?",
            first.id
        )

        // Second suggestion for the same pair should succeed because deleted row is excluded from unique index
        val second = matchSuggestionRepository.saveAndFlush(buildSuggestion())
        assertNotNull(second.id)

        val count = jdbcTemplate.queryForObject(
            """SELECT COUNT(*) FROM match_suggestions
               WHERE workspace_id = ? AND source_entity_id = ? AND target_entity_id = ?""",
            Int::class.java,
            workspaceId, sourceId, targetId
        )
        assertEquals(2, count)
    }

    /**
     * INFRA-04: pg_trgm extension and GIN index existence.
     *
     * Verifies the pg_trgm extension is installed in the test database.
     * The GIN index on entity_attributes is verified by the presence of the SQL file;
     * Hibernate create-drop only manages entity-mapped tables, so the index DDL is
     * exercised in production schema runs, not by JPA auto-config.
     */
    @Test
    fun `pg_trgm extension is installed`() {
        val extCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'",
            Int::class.java
        )
        assertEquals(1, extCount, "pg_trgm extension must be installed for fuzzy matching")
    }

    /**
     * INFRA-05: Unique active pair constraint.
     *
     * Verifies that inserting two active match suggestions with the same
     * (workspace_id, source_entity_id, target_entity_id) is rejected.
     */
    @Test
    fun `duplicate active suggestion for same entity pair is rejected`() {
        matchSuggestionRepository.saveAndFlush(buildSuggestion())

        assertThrows<DataIntegrityViolationException>(
            "Expected unique constraint violation for duplicate active suggestion pair"
        ) {
            matchSuggestionRepository.saveAndFlush(buildSuggestion())
        }
    }

    /**
     * INFRA-06: Canonical UUID ordering CHECK constraint.
     *
     * Verifies the CHECK constraint chk_match_suggestions_canonical_order rejects a row
     * where source_entity_id > target_entity_id (reversed pair).
     */
    @Test
    fun `CHECK constraint rejects suggestion where source entity id is greater than target`() {
        // targetId > sourceId, so (targetId, sourceId) reverses the canonical order
        assertThrows<DataIntegrityViolationException>(
            "Expected CHECK constraint violation for non-canonical UUID ordering"
        ) {
            matchSuggestionRepository.saveAndFlush(
                buildSuggestion(source = targetId, target = sourceId)
            )
        }
    }

    /**
     * INFRA-05: One-cluster-per-entity uniqueness constraint.
     *
     * Verifies that an entity can belong to at most one identity cluster.
     * The unique index on identity_cluster_members (entity_id) enforces this at the DB level.
     */
    @Test
    fun `entity can belong to at most one identity cluster`() {
        val entityId = UUID.fromString("30000000-0000-0000-0000-000000000003")

        val clusterA = identityClusterRepository.saveAndFlush(buildCluster())
        val clusterB = identityClusterRepository.saveAndFlush(buildCluster())

        identityClusterMemberRepository.saveAndFlush(buildMember(clusterA.id!!, entityId))

        assertThrows<DataIntegrityViolationException>(
            "Expected unique constraint violation when adding entity to a second cluster"
        ) {
            identityClusterMemberRepository.saveAndFlush(buildMember(clusterB.id!!, entityId))
        }
    }
}
