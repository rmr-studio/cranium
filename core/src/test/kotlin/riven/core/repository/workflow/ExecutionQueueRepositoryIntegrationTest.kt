package riven.core.repository.workflow

import io.github.oshai.kotlinlogging.KLogger
import org.assertj.core.api.Assertions.assertThat
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
import riven.core.entity.connotation.EntityConnotationEntity
import riven.core.enums.connotation.ConnotationStatus
import riven.core.enums.workflow.ExecutionJobType
import riven.core.enums.workflow.ExecutionQueueStatus
import riven.core.models.connotation.EntityMetadata
import riven.core.models.connotation.EntityMetadataSnapshot
import riven.core.models.connotation.SentimentMetadata
import riven.core.repository.connotation.EntityConnotationRepository
import riven.core.service.util.SchemaInitializer
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Singleton PostgreSQL container shared across the queue repository integration tests.
 *
 * Runs the full production schema via [SchemaInitializer] so the JSONB-path query has
 * the real `entities`, `entity_connotation`, and `execution_queue` tables (with the
 * partial unique index required by `ON CONFLICT DO NOTHING`).
 */
private object ExecutionQueueRepositoryTestContainer {
    val instance: PostgreSQLContainer = PostgreSQLContainer(
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    )
        .withDatabaseName("riven_queue_repo_test")
        .withUsername("test")
        .withPassword("test")

    init {
        instance.start()
    }
}

/**
 * Minimal Spring config for the queue repository integration test. Loads the JPA layer
 * for the connotation, workflow, and entity repositories — no services, no security,
 * no Temporal.
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
        "riven.core.repository.workflow",
        "riven.core.repository.connotation",
    ]
)
@EntityScan(
    basePackages = [
        "riven.core.entity.workflow",
        "riven.core.entity.connotation",
    ]
)
@EnableJpaAuditing(
    auditorAwareRef = "execQueueAuditorProvider",
    dateTimeProviderRef = "execQueueDateTimeProvider",
)
class ExecutionQueueRepositoryIntegrationTestConfig {

    @Bean
    fun execQueueAuditorProvider(): AuditorAware<UUID> = AuditorAware { Optional.empty() }

    @Bean
    fun execQueueDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }

    /**
     * KLogger bean — the JsonBinaryType / JPA layer uses it indirectly only through other
     * services; defined here for safety since LoggerConfig is not on the component scan.
     */
    @Bean
    fun klogger(): KLogger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
}

/**
 * Integration test for [ExecutionQueueRepository.enqueueByMetadataVersionMismatch].
 *
 * Verifies the JSONB-path version-mismatch enqueue query against a real Postgres
 * (Testcontainers) — H2 does not reliably support `IS DISTINCT FROM` on JSONB paths,
 * which is the operator that lets the query treat null-version snapshots the same as
 * differing-version snapshots.
 *
 * The test uses [SchemaInitializer] to apply the production DDL, including the partial
 * unique index `uq_execution_queue_pending_identity_match` that backs the
 * `ON CONFLICT DO NOTHING` idempotency clause.
 */
@SpringBootTest(
    classes = [ExecutionQueueRepositoryIntegrationTestConfig::class],
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
class ExecutionQueueRepositoryIntegrationTest {

    @Autowired
    private lateinit var executionQueueRepository: ExecutionQueueRepository

    @Autowired
    private lateinit var entityConnotationRepository: EntityConnotationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val workspaceId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000001")
    private val entityTypeId: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000010")
    private val identifierKey: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000020")

    companion object {
        private var schemaInitialized = false

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val container = ExecutionQueueRepositoryTestContainer.instance
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
    fun seedWorkspaceAndEntityType() {
        jdbcTemplate.execute(
            """
            INSERT INTO workspaces (id, name, member_count, created_at, updated_at)
            VALUES ('$workspaceId', 'Queue Repo Test Workspace', 1, now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO entity_types (
                id, key, workspace_id, identifier_key, display_name_singular, display_name_plural,
                schema, semantic_group, lifecycle_domain, source_type
            )
            VALUES (
                '$entityTypeId', 'queue_repo_test_type', '$workspaceId', '$identifierKey',
                'Test', 'Tests', '{}'::jsonb, 'UNCATEGORIZED', 'UNCATEGORIZED', 'USER_CREATED'
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
    }

    @BeforeEach
    fun cleanup() {
        // Order matters: child tables first (FKs cascade, but explicit clean is more predictable).
        jdbcTemplate.execute("DELETE FROM execution_queue WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entity_connotation WHERE workspace_id = '$workspaceId'")
        jdbcTemplate.execute("DELETE FROM entities WHERE workspace_id = '$workspaceId'")
    }

    // ------ Helpers ------

    /**
     * Inserts an `entities` row directly via JDBC. Bypasses the JPA layer so we don't need
     * the full EntityEntity component graph wired in this minimal test config — the query
     * under test only joins on entities.id / entities.deleted, which we control here.
     */
    private fun insertEntity(deleted: Boolean = false): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key, deleted, source_type
            )
            VALUES (?, ?, ?, ?, ?, ?, 'USER_CREATED')
            """.trimIndent(),
            id, workspaceId, entityTypeId, "queue_repo_test_type", identifierKey, deleted,
        )
        return id
    }

    private fun saveMetadata(entityId: UUID, sentimentVersion: String?) {
        val snapshot = EntityMetadataSnapshot(
            snapshotVersion = "v1",
            metadata = EntityMetadata(
                sentiment = SentimentMetadata(
                    analysisVersion = sentimentVersion,
                    status = ConnotationStatus.NOT_APPLICABLE,
                ),
            ),
            embeddedAt = ZonedDateTime.now(),
        )
        entityConnotationRepository.saveAndFlush(
            EntityConnotationEntity(
                entityId = entityId,
                workspaceId = workspaceId,
                connotationMetadata = snapshot,
            )
        )
    }

    private fun pendingQueueRowsForEntity(entityId: UUID): List<UUID> {
        return jdbcTemplate.queryForList(
            """
            SELECT id FROM execution_queue
            WHERE workspace_id = ? AND entity_id = ? AND job_type = 'ENRICHMENT' AND status = 'PENDING'
            """.trimIndent(),
            UUID::class.java,
            workspaceId, entityId,
        )
    }

    // ------ Tests ------

    /**
     * Original behaviour: a workspace-wide config bump (e.g. `analysisVersion` "v0" → "v1")
     * needs every snapshot still stamped with the old version to be re-enqueued, while
     * snapshots already at the new version are skipped.
     *
     * Verifies: only the v0 snapshot is enqueued; the v1 snapshot is left alone.
     */
    @Test
    fun `enqueues entities whose metadata version differs`() {
        val staleEntityId = insertEntity()
        val freshEntityId = insertEntity()
        saveMetadata(staleEntityId, sentimentVersion = "v0")
        saveMetadata(freshEntityId, sentimentVersion = "v1")

        val inserted = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )

        assertThat(inserted).isEqualTo(1)
        assertThat(pendingQueueRowsForEntity(staleEntityId)).hasSize(1)
        assertThat(pendingQueueRowsForEntity(freshEntityId)).isEmpty()
    }

    /**
     * Snapshots that have never been analysed lack an `analysisVersion` stamp (null).
     * `IS DISTINCT FROM` treats null as different from any concrete version, so these
     * rows must also be enqueued. Without `IS DISTINCT FROM` (i.e. plain `<>`) the
     * comparison would yield SQL null and the row would silently be skipped.
     */
    @Test
    fun `enqueues entities whose metadata version is null`() {
        val nullVersionEntityId = insertEntity()
        saveMetadata(nullVersionEntityId, sentimentVersion = null)

        val inserted = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )

        assertThat(inserted).isEqualTo(1)
        assertThat(pendingQueueRowsForEntity(nullVersionEntityId)).hasSize(1)
    }

    /**
     * Entities that don't yet have a connotation snapshot row are out of scope for this
     * helper — they're enqueued via the regular `enqueueEnrichmentByEntityType` path.
     * The INNER JOIN on `entity_connotation` excludes them.
     */
    @Test
    fun `skips entities with no snapshot`() {
        insertEntity() // no snapshot saved

        val inserted = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )

        assertThat(inserted).isEqualTo(0)
    }

    /**
     * Soft-deleted entities must not be re-enqueued — the join filters on
     * `entities.deleted = false`. Without the filter, a deleted entity with a stale
     * snapshot would generate a queue item that no consumer can process.
     */
    @Test
    fun `skips soft-deleted entities`() {
        val deletedEntityId = insertEntity(deleted = true)
        saveMetadata(deletedEntityId, sentimentVersion = "v0")

        val inserted = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )

        assertThat(inserted).isEqualTo(0)
    }

    /**
     * Re-running the query while prior PENDING items are still in the queue must be a no-op
     * — the partial unique index `uq_execution_queue_pending_identity_match` on
     * (workspace_id, entity_id, job_type) WHERE status = 'PENDING' combines with
     * `ON CONFLICT DO NOTHING` to guarantee idempotency on retry.
     */
    @Test
    fun `is idempotent on repeat call`() {
        val staleEntityId = insertEntity()
        saveMetadata(staleEntityId, sentimentVersion = "v0")

        val firstInsert = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )
        val secondInsert = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )

        assertThat(firstInsert).isEqualTo(1)
        assertThat(secondInsert).isEqualTo(0)
        assertThat(pendingQueueRowsForEntity(staleEntityId)).hasSize(1)
    }

    /**
     * The query must be workspace-scoped — snapshots in other workspaces with stale
     * versions must NOT be enqueued under [workspaceId]. We seed a second workspace and
     * a stale snapshot in it, then assert nothing is enqueued for our test workspace.
     */
    @Test
    fun `does not enqueue snapshots from other workspaces`() {
        val otherWorkspaceId = UUID.fromString("c0000000-0000-0000-0000-000000000999")
        val otherEntityTypeId = UUID.fromString("c0000000-0000-0000-0000-000000000998")
        jdbcTemplate.execute(
            """
            INSERT INTO workspaces (id, name, member_count, created_at, updated_at)
            VALUES ('$otherWorkspaceId', 'Other Workspace', 1, now(), now())
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            INSERT INTO entity_types (
                id, key, workspace_id, identifier_key, display_name_singular, display_name_plural,
                schema, semantic_group, lifecycle_domain, source_type
            )
            VALUES (
                '$otherEntityTypeId', 'queue_repo_other_type', '$otherWorkspaceId', '$identifierKey',
                'Other', 'Others', '{}'::jsonb, 'UNCATEGORIZED', 'UNCATEGORIZED', 'USER_CREATED'
            )
            ON CONFLICT (id) DO NOTHING
            """.trimIndent()
        )
        val otherEntityId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO entities (
                id, workspace_id, type_id, type_key, identifier_key, deleted, source_type
            )
            VALUES (?, ?, ?, ?, ?, false, 'USER_CREATED')
            """.trimIndent(),
            otherEntityId, otherWorkspaceId, otherEntityTypeId,
            "queue_repo_other_type", identifierKey,
        )
        val snapshot2 = EntityMetadataSnapshot(
            metadata = EntityMetadata(
                sentiment = SentimentMetadata(
                    analysisVersion = "v0",
                    status = ConnotationStatus.NOT_APPLICABLE,
                ),
            ),
            embeddedAt = ZonedDateTime.now(),
        )
        entityConnotationRepository.saveAndFlush(
            EntityConnotationEntity(
                entityId = otherEntityId,
                workspaceId = otherWorkspaceId,
                connotationMetadata = snapshot2,
            )
        )

        val inserted = executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )

        assertThat(inserted).isEqualTo(0)
        // Sanity: no queue rows landed in the test workspace at all.
        val rowsInTestWorkspace = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM execution_queue WHERE workspace_id = ?",
            Long::class.java,
            workspaceId,
        )
        assertThat(rowsInTestWorkspace).isEqualTo(0L)

        // Cleanup
        jdbcTemplate.execute("DELETE FROM execution_queue WHERE workspace_id = '$otherWorkspaceId'")
        jdbcTemplate.execute("DELETE FROM entity_connotation WHERE workspace_id = '$otherWorkspaceId'")
        jdbcTemplate.execute("DELETE FROM entities WHERE workspace_id = '$otherWorkspaceId'")
    }

    /**
     * Sanity check on the queue entity itself — verifies the inserted row has the expected
     * job type and status enum values via the JPA layer (the native INSERT writes raw strings).
     */
    @Test
    fun `inserted queue rows have ENRICHMENT job type and PENDING status`() {
        val staleEntityId = insertEntity()
        saveMetadata(staleEntityId, sentimentVersion = "v0")

        executionQueueRepository.enqueueByMetadataVersionMismatch(
            metadataKey = "SENTIMENT",
            currentVersion = "v1",
            workspaceId = workspaceId,
        )

        val pending = executionQueueRepository
            .findByWorkspaceIdAndStatusOrderByCreatedAtAsc(workspaceId, ExecutionQueueStatus.PENDING)
            .filter { it.entityId == staleEntityId }
        assertThat(pending).hasSize(1)
        assertThat(pending.single().jobType).isEqualTo(ExecutionJobType.ENRICHMENT)
        assertThat(pending.single().status).isEqualTo(ExecutionQueueStatus.PENDING)
    }
}
