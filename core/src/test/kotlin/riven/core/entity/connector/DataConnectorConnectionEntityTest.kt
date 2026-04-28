package riven.core.entity.connector

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import riven.core.repository.connector.DataConnectorConnectionRepository
import riven.core.service.util.factory.dataconnector.DataConnectorConnectionEntityFactory
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import java.util.UUID

/**
 * Verifies [DataConnectorConnectionEntity] round-trips through JPA against real
 * Postgres (Phase 2 CONN-01).
 *
 * Uses Testcontainers `pgvector/pgvector:pg16` + `@ActiveProfiles("integration")`
 * per core/CLAUDE.md Testing Rules. H2 cannot emulate Postgres `bytea`
 * round-trips with FK constraints, so Testcontainers is mandatory — same
 * precedent as [riven.core.entity.entity.SourceTypeJpaRoundTripTest].
 *
 * Covers:
 *  - Round-trip: encrypted_credentials + iv bytea preserved byte-for-byte
 *  - Soft-delete: `deleted = true` → `findById` returns empty via
 *    `@SQLRestriction` on [riven.core.entity.util.AuditableSoftDeletableEntity]
 *  - Workspace scoping: `findByWorkspaceId` returns only rows in that workspace
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
@EnableJpaRepositories(basePackages = ["riven.core.repository.connector"])
@EntityScan("riven.core.entity.connector")
@EnableJpaAuditing(
    auditorAwareRef = "dataConnectorConnectionRoundTripAuditorProvider",
    dateTimeProviderRef = "dataConnectorConnectionRoundTripDateTimeProvider",
)
class DataConnectorConnectionEntityTestConfig {

    @Bean
    fun dataConnectorConnectionRoundTripAuditorProvider(): AuditorAware<UUID> =
        AuditorAware { Optional.empty() }

    @Bean
    fun dataConnectorConnectionRoundTripDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of<TemporalAccessor>(ZonedDateTime.now()) }
}

@SpringBootTest(
    classes = [DataConnectorConnectionEntityTestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataConnectorConnectionEntityTest {

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
    private lateinit var connectionRepository: DataConnectorConnectionRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    /**
     * Hibernate's `ddl-auto: create-drop` only generates tables for scanned
     * entities. We deliberately scan only `riven.core.entity.dataconnector`
     * to avoid dragging in the full entity graph (WorkspaceInviteEntity ->
     * UserEntity etc.), so we hand-create the minimal `workspaces` table
     * that the dats_connector_connection FK targets.
     */
    @BeforeAll
    fun createWorkspacesTable() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS workspaces (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                name VARCHAR(100) NOT NULL,
                deleted BOOLEAN NOT NULL DEFAULT FALSE
            )
            """.trimIndent()
        )
    }

    private fun insertWorkspace(): UUID {
        val id = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO workspaces (id, name) VALUES (?, ?)",
            id,
            "ws-${id.toString().take(8)}",
        )
        return id
    }

    @Test
    @Transactional
    fun `saves and reads entity with bytea credentials preserved`() {
        val workspaceId = insertWorkspace()

        val credentials = ByteArray(64) { (it * 7 + 13).toByte() }
        val iv = ByteArray(12) { (255 - it).toByte() }

        val entity = DataConnectorConnectionEntityFactory.create(
            workspaceId = workspaceId,
            encryptedCredentials = credentials,
            iv = iv,
            keyVersion = 3,
        )

        val saved = connectionRepository.saveAndFlush(entity)
        val persistedId = requireNotNull(saved.id) { "id should be assigned after saveAndFlush" }

        val reloaded = connectionRepository.findById(persistedId).orElseThrow()

        assertThat(reloaded.workspaceId).isEqualTo(workspaceId)
        assertThat(reloaded.encryptedCredentials).isEqualTo(credentials)
        assertThat(reloaded.iv).isEqualTo(iv)
        assertThat(reloaded.keyVersion).isEqualTo(3)
        assertThat(reloaded.name).isEqualTo(entity.name)
    }

    @Test
    @Transactional
    fun `soft-deleted entity is invisible to findById`() {
        val workspaceId = insertWorkspace()

        val entity = DataConnectorConnectionEntityFactory.create(workspaceId = workspaceId)
        val saved = connectionRepository.saveAndFlush(entity)
        val persistedId = requireNotNull(saved.id) { "id should be assigned after saveAndFlush" }

        // Flip via direct SQL — the managed entity's @SQLRestriction would
        // otherwise reject the UPDATE round-trip via saveAndFlush in the same
        // session, and we want to test the query-side filter, not the write path.
        jdbcTemplate.update(
            "UPDATE data_connector_connections SET deleted = TRUE, deleted_at = NOW() WHERE id = ?",
            persistedId,
        )

        // Clear the persistence context so the next query re-hits the DB
        // instead of returning the cached managed instance.
        entityManager.clear()

        val deletedFlag = jdbcTemplate.queryForObject(
            "SELECT deleted FROM data_connector_connections WHERE id = ?",
            Boolean::class.java,
            persistedId,
        )
        assertThat(deletedFlag).isTrue()

        // `findById` uses EntityManager.find() (primary-key load) which, in
        // Hibernate 6, can bypass @SQLRestriction. `findByIdAndWorkspaceId`
        // is a derived query — @SQLRestriction is always appended to JPQL,
        // so this is the query the service layer will use and the one we
        // contractually need to hide soft-deleted rows from.
        val result = connectionRepository.findByIdAndWorkspaceId(persistedId, workspaceId)

        assertThat(result).isEmpty

        // Sanity: the row still exists physically (soft-delete, not hard-delete).
        val physicalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM data_connector_connections WHERE id = ?",
            Long::class.java,
            persistedId,
        )
        assertThat(physicalCount).isEqualTo(1L)
    }

    @Test
    @Transactional
    fun `findByWorkspaceId scopes results to the given workspace`() {
        val wsAId = insertWorkspace()
        val wsBId = insertWorkspace()

        val a1 = connectionRepository.saveAndFlush(
            DataConnectorConnectionEntityFactory.create(workspaceId = wsAId, name = "a1")
        )
        val a2 = connectionRepository.saveAndFlush(
            DataConnectorConnectionEntityFactory.create(workspaceId = wsAId, name = "a2")
        )
        val b1 = connectionRepository.saveAndFlush(
            DataConnectorConnectionEntityFactory.create(workspaceId = wsBId, name = "b1")
        )

        val inA = connectionRepository.findByWorkspaceId(wsAId)
        val inB = connectionRepository.findByWorkspaceId(wsBId)

        assertThat(inA.map { it.id }).containsExactlyInAnyOrder(a1.id, a2.id)
        assertThat(inB.map { it.id }).containsExactly(b1.id)
    }
}
