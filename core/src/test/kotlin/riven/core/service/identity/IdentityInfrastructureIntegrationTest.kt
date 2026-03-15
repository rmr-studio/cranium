package riven.core.service.identity

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Wave 0 integration test scaffold for identity infrastructure schema (INFRA-02, INFRA-04, INFRA-05, INFRA-06).
 *
 * Uses Testcontainers PostgreSQL to validate that schema constraints, indexes, and CHECK constraints
 * are correctly applied to the identity tables created in plan 01-02.
 *
 * Covers:
 * - INFRA-02: Dedup index silently skips duplicate PENDING identity match jobs
 * - INFRA-04: pg_trgm GIN index exists on entity_attributes
 * - INFRA-05: Unique pair constraint on match_suggestions; unique cluster membership
 * - INFRA-06: CHECK constraint rejects source_entity_id > target_entity_id
 *
 * All tests are disabled until plan 01-02 (identity schema) is implemented.
 * Enable by removing @Disabled annotations after identity tables are created.
 */
@SpringBootTest(
    classes = [IdentityInfrastructureIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
class IdentityInfrastructureIntegrationTest {

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
    class TestConfig

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

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    /**
     * INFRA-02: Verifies the partial unique index on execution_queue prevents duplicate
     * PENDING identity match jobs for the same (workspace_id, entity_id, job_type).
     *
     * After plan 01-02: attempt to insert two PENDING rows with the same composite key.
     * The second insert should fail with a unique constraint violation (or be silently
     * skipped via ON CONFLICT DO NOTHING depending on the enqueue implementation).
     */
    @Test
    @Disabled("Wave 0 scaffold — enable after 01-02 implementation")
    fun `dedup index silently skips duplicate pending identity match job`() {
        // After 01-02: insert a PENDING identity match queue entry, then insert the same
        // (workspace_id, entity_id, job_type) again. Verify no duplicate row exists.
        // The partial unique index WHERE status = 'PENDING' enforces dedup atomically.
        TODO("Enable after 01-02: insert duplicate PENDING IDENTITY_MATCH job and verify dedup constraint fires")
    }

    /**
     * INFRA-06: Verifies the CHECK constraint on match_suggestions rejects rows
     * where source_entity_id > target_entity_id (canonical UUID ordering enforced by DB).
     *
     * After plan 01-02: attempt to insert a match_suggestions row where
     * source_entity_id::text > target_entity_id::text and verify a PSQLException is thrown.
     */
    @Test
    @Disabled("Wave 0 scaffold — enable after 01-02 implementation")
    fun `CHECK constraint rejects source_entity_id greater than target_entity_id`() {
        // After 01-02: insert match_suggestions with source > target UUID.
        // Expect org.postgresql.util.PSQLException with constraint violation message.
        TODO("Enable after 01-02: insert match_suggestion with source > target and verify CHECK constraint violation")
    }

    /**
     * INFRA-04: Verifies the pg_trgm GIN index idx_entity_attributes_trgm exists on entity_attributes.
     *
     * After plan 01-02: query pg_indexes system catalog to confirm the index was created.
     * This ensures fuzzy text matching on attribute values will use the GIN index.
     */
    @Test
    @Disabled("Wave 0 scaffold — enable after 01-02 implementation")
    fun `pg_trgm GIN index exists on entity_attributes`() {
        // After 01-02: query pg_indexes WHERE tablename = 'entity_attributes' AND indexname = 'idx_entity_attributes_trgm'
        // Assert the result set is non-empty.
        TODO("Enable after 01-02: query pg_indexes and verify idx_entity_attributes_trgm exists")
    }

    /**
     * INFRA-05: Verifies the unique pair constraint prevents duplicate active match suggestions
     * for the same (workspace_id, source_entity_id, target_entity_id) when deleted = false.
     *
     * After plan 01-02: insert two match_suggestions rows with the same workspace + pair.
     * Expect a unique constraint violation on the second insert.
     */
    @Test
    @Disabled("Wave 0 scaffold — enable after 01-02 implementation")
    fun `unique pair constraint prevents duplicate active suggestions`() {
        // After 01-02: insert two match_suggestions with same (workspace_id, source_entity_id, target_entity_id)
        // and deleted = false. Assert PSQLException is thrown on the second insert.
        TODO("Enable after 01-02: insert duplicate match_suggestion pair and verify unique constraint violation")
    }

    /**
     * INFRA-05: Verifies that an entity can belong to at most one identity cluster
     * via the unique constraint on identity_cluster_members (entity_id, workspace_id).
     *
     * After plan 01-02: insert an entity into two different clusters using the same entity_id.
     * Expect a unique constraint violation on the second membership insert.
     */
    @Test
    @Disabled("Wave 0 scaffold — enable after 01-02 implementation")
    fun `entity can belong to at most one identity cluster`() {
        // After 01-02: insert two identity_cluster_members rows for the same entity_id.
        // Assert PSQLException is thrown on the second insert due to unique constraint.
        TODO("Enable after 01-02: insert entity into two clusters and verify unique membership constraint")
    }
}
